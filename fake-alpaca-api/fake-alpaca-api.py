#!/usr/bin/env python3
"""A stateful, in-memory mock of Alpaca's Paper Trading API.

This is intentionally small enough to run as a single Kubernetes pod and has
no third-party dependencies or outbound network calls.  It implements the
Trading API routes used by this repository:

  * POST/GET /v2/orders
  * GET /v2/account and GET /v2/positions
  * GET/POST/PUT/DELETE /v2/watchlists and watchlist asset operations
  * the binary paper-trading WebSocket at /stream (trade_updates)

The shapes and protocol are based on Alpaca's official Trading API reference:
https://docs.alpaca.markets/us/reference and
https://docs.alpaca.markets/us/docs/websocket-streaming

Run locally:
    python3 fake-alpaca-api.py --host 0.0.0.0 --port 8080

Point all three services at http://fake-alpaca-api:8080.  For the execution
service, set both ALPACA_API_ORDER_ENDPOINT=http://fake-alpaca-api:8080/v2/orders
and ALPACA_API_STREAM_URL=ws://fake-alpaca-api:8080/stream.

Use --require-auth (and optionally --api-key/--api-secret) when the cluster
should reject missing or invalid APCA-API-KEY-ID/APCA-API-SECRET-KEY headers.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import logging
import os
import re
import socket
import struct
import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse


LOG = logging.getLogger("fake-alpaca-api")
JSON_TYPE = "application/json"
OPEN_ORDER_STATUSES = {"new", "accepted", "pending_new", "partially_filled"}
TERMINAL_ORDER_STATUSES = {"filled", "canceled", "expired", "rejected"}
ORDER_TYPES = {"market", "limit", "stop", "stop_limit", "trailing_stop"}
ORDER_SIDES = {"buy", "sell"}
TIME_IN_FORCE = {"day", "gtc", "opg", "cls", "ioc", "fok"}
SYMBOL = re.compile(r"^[A-Z][A-Z.]{0,15}$")
MAX_REQUEST_BODY_BYTES = 1_048_576


def now() -> str:
    """Return the UTC RFC 3339 timestamp format Alpaca uses."""
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def decimal_text(value: Decimal) -> str:
    """Serialize Decimal without exponent notation or insignificant zeroes."""
    rendered = format(value, "f")
    return rendered.rstrip("0").rstrip(".") if "." in rendered else rendered


def as_decimal(value: Any, field_name: str) -> Decimal:
    try:
        parsed = Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError) as exc:
        raise ApiError(422, 42210000, f"{field_name} must be a positive number") from exc
    if not parsed.is_finite() or parsed <= 0:
        raise ApiError(422, 42210000, f"{field_name} must be a positive number")
    return parsed


class ApiError(Exception):
    def __init__(self, status: int, code: int, message: str) -> None:
        self.status = status
        self.code = code
        self.message = message
        super().__init__(message)


@dataclass
class WebSocketPeer:
    connection: socket.socket
    lock: threading.Lock = field(default_factory=threading.Lock)
    subscribed: bool = False
    authenticated: bool = False


class PaperAccount:
    """Thread-safe state and Alpaca-shaped resource serializers."""

    ACCOUNT_ID = "11111111-1111-4111-8111-111111111111"

    def __init__(self, fill_mode: str, prices: dict[str, Decimal]) -> None:
        self.lock = threading.RLock()
        self.fill_mode = fill_mode
        self.initial_cash = Decimal("100000")
        self.cash = self.initial_cash
        self.prices = prices
        self.orders: dict[str, dict[str, Any]] = {}
        self.positions: dict[str, dict[str, Any]] = {}
        self.watchlists: dict[str, dict[str, Any]] = {}
        self.listeners: list[WebSocketPeer] = []

    def reset(self) -> None:
        with self.lock:
            self.cash = self.initial_cash
            self.orders.clear()
            self.positions.clear()
            self.watchlists.clear()

    def seed(self, cash: Any, positions: Any) -> None:
        """Replace mutable broker state with an explicit test fixture."""
        if not isinstance(positions, list):
            raise ApiError(422, 42210000, "positions must be an array")

        if cash is None:
            seeded_cash = self.initial_cash
        else:
            try:
                seeded_cash = Decimal(str(cash))
            except (InvalidOperation, ValueError, TypeError) as exc:
                raise ApiError(422, 42210000, "cash must be a non-negative number") from exc
            if not seeded_cash.is_finite() or seeded_cash < 0:
                raise ApiError(422, 42210000, "cash must be a non-negative number")
        seeded_positions: dict[str, dict[str, Any]] = {}
        for value in positions:
            if not isinstance(value, dict):
                raise ApiError(422, 42210000, "each position must be an object")

            symbol = str(value.get("symbol", "")).strip().upper()
            if not SYMBOL.fullmatch(symbol):
                raise ApiError(422, 42210000, "invalid position symbol")
            if symbol in seeded_positions:
                raise ApiError(422, 42210000, "duplicate position symbol")

            try:
                quantity = Decimal(str(value.get("qty")))
            except (InvalidOperation, ValueError, TypeError) as exc:
                raise ApiError(422, 42210000, "position qty must be a non-zero number") from exc
            if not quantity.is_finite() or quantity == 0:
                raise ApiError(422, 42210000, "position qty must be a non-zero number")

            average_entry_price = as_decimal(
                value.get("avg_entry_price", self.price_for(symbol)),
                "avg_entry_price",
            )
            seeded_positions[symbol] = {
                "asset_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "fake-alpaca:" + symbol)),
                "symbol": symbol,
                "qty": quantity,
                "avg_entry_price": average_entry_price,
            }

        with self.lock:
            self.cash = seeded_cash
            self.orders.clear()
            self.positions = seeded_positions
            self.watchlists.clear()

    def account(self) -> dict[str, Any]:
        with self.lock:
            long_value = sum(
                (position["qty"] * self.price_for(position["symbol"])
                 for position in self.positions.values() if position["qty"] > 0), Decimal("0")
            )
            short_value = sum(
                (position["qty"] * self.price_for(position["symbol"])
                 for position in self.positions.values() if position["qty"] < 0), Decimal("0")
            )
            equity = self.cash + long_value + short_value
            buying_power = max(equity * Decimal("2"), Decimal("0"))
            return {
                "id": self.ACCOUNT_ID,
                "account_number": "PAFAKE-001",
                "status": "ACTIVE",
                "currency": "USD",
                "cash": decimal_text(self.cash),
                "buying_power": decimal_text(buying_power),
                "non_marginable_buying_power": decimal_text(max(self.cash, Decimal("0"))),
                "portfolio_value": decimal_text(equity),
                "equity": decimal_text(equity),
                "last_equity": decimal_text(equity),
                "long_market_value": decimal_text(long_value),
                "short_market_value": decimal_text(short_value),
                "initial_margin": "0",
                "maintenance_margin": "0",
                "last_maintenance_margin": "0",
                "sma": "0",
                "daytrade_count": 0,
                "account_blocked": False,
                "trading_blocked": False,
                "trade_suspended_by_user": False,
                "shorting_enabled": True,
                "multiplier": "2",
                "pattern_day_trader": False,
            }

    def price_for(self, symbol: str) -> Decimal:
        return self.prices.get(symbol, Decimal("100"))

    def position_list(self) -> list[dict[str, Any]]:
        with self.lock:
            return [self._position_response(position) for position in self.positions.values() if position["qty"] != 0]

    def _position_response(self, position: dict[str, Any]) -> dict[str, Any]:
        quantity = position["qty"]
        current_price = self.price_for(position["symbol"])
        market_value = quantity * current_price
        cost_basis = quantity * position["avg_entry_price"]
        unrealized = market_value - cost_basis
        return {
            "asset_id": position["asset_id"],
            "symbol": position["symbol"],
            "exchange": "NASDAQ",
            "asset_class": "us_equity",
            "qty": decimal_text(abs(quantity)),
            "avg_entry_price": decimal_text(position["avg_entry_price"]),
            "side": "long" if quantity > 0 else "short",
            "market_value": decimal_text(abs(market_value)),
            "cost_basis": decimal_text(abs(cost_basis)),
            "unrealized_pl": decimal_text(abs(unrealized) if quantity < 0 else unrealized),
            "unrealized_plpc": "0" if not cost_basis else decimal_text(unrealized / cost_basis),
            "current_price": decimal_text(current_price),
            "lastday_price": decimal_text(current_price),
            "change_today": "0",
        }

    def create_order(self, payload: dict[str, Any]) -> dict[str, Any]:
        symbol = str(payload.get("symbol", "")).strip().upper()
        side = str(payload.get("side", "")).strip().lower()
        order_type = str(payload.get("type", "market")).strip().lower()
        tif = str(payload.get("time_in_force", "day")).strip().lower()
        if not SYMBOL.fullmatch(symbol):
            raise ApiError(422, 42210000, "invalid symbol")
        if side not in ORDER_SIDES:
            raise ApiError(422, 42210000, "side must be buy or sell")
        if order_type not in ORDER_TYPES:
            raise ApiError(422, 42210000, "invalid order type")
        if tif not in TIME_IN_FORCE:
            raise ApiError(422, 42210000, "invalid time_in_force")
        if "qty" not in payload:
            raise ApiError(422, 42210000, "qty is required by this mock")
        quantity = as_decimal(payload["qty"], "qty")
        limit_price = None
        if order_type in {"limit", "stop_limit"}:
            if payload.get("limit_price") is None:
                raise ApiError(422, 42210000, "limit_price is required for limit orders")
            limit_price = as_decimal(payload["limit_price"], "limit_price")
        client_id = str(payload.get("client_order_id") or uuid.uuid4())
        if len(client_id) > 128:
            raise ApiError(422, 42210000, "client_order_id must be 128 characters or fewer")
        with self.lock:
            if any(existing["client_order_id"] == client_id for existing in self.orders.values()):
                raise ApiError(422, 42210000, "client_order_id must be unique")
            estimated_price = limit_price or self.price_for(symbol)
            if side == "buy" and quantity * estimated_price > self.cash * Decimal("2"):
                raise ApiError(403, 40310000, "insufficient buying power")
            timestamp = now()
            order_id = str(uuid.uuid4())
            should_fill = self.fill_mode == "immediate" and self._is_marketable(side, order_type, limit_price, symbol)
            order = {
                "id": order_id,
                "client_order_id": client_id,
                "created_at": timestamp,
                "updated_at": timestamp,
                "submitted_at": timestamp,
                "filled_at": None,
                "expired_at": None,
                "canceled_at": None,
                "asset_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "fake-alpaca:" + symbol)),
                "symbol": symbol,
                "asset_class": "us_equity",
                "notional": None,
                "qty": decimal_text(quantity),
                "filled_qty": "0",
                "filled_avg_price": None,
                "order_class": "simple",
                "order_type": order_type,
                "type": order_type,
                "side": side,
                "time_in_force": tif,
                "limit_price": decimal_text(limit_price) if limit_price is not None else None,
                "stop_price": None,
                "status": "new",
                "extended_hours": bool(payload.get("extended_hours", False)),
                "legs": None,
            }
            self.orders[order_id] = order
            if should_fill:
                self._fill_locked(order, self.price_for(symbol))
            response = self.copy(order)
        self.publish_trade_update("fill" if should_fill else "new", response)
        return response

    def _is_marketable(self, side: str, order_type: str, limit_price: Decimal | None, symbol: str) -> bool:
        if order_type == "market":
            return True
        if order_type != "limit" or limit_price is None:
            return False
        market_price = self.price_for(symbol)
        return limit_price >= market_price if side == "buy" else limit_price <= market_price

    def fill(self, order_id: str, price: Decimal | None = None) -> dict[str, Any]:
        with self.lock:
            order = self.orders.get(order_id)
            if order is None:
                raise ApiError(404, 40410000, "order not found")
            if order["status"] in TERMINAL_ORDER_STATUSES:
                raise ApiError(422, 42210000, "order is already final")
            self._fill_locked(order, price or self.price_for(order["symbol"]))
            response = self.copy(order)
        self.publish_trade_update("fill", response)
        return response

    def _fill_locked(self, order: dict[str, Any], price: Decimal) -> None:
        quantity = Decimal(order["qty"])
        signed_quantity = quantity if order["side"] == "buy" else -quantity
        position = self.positions.get(order["symbol"])
        if position is None:
            position = {
                "asset_id": order["asset_id"], "symbol": order["symbol"],
                "qty": Decimal("0"), "avg_entry_price": price,
            }
            self.positions[order["symbol"]] = position
        old_quantity = position["qty"]
        new_quantity = old_quantity + signed_quantity
        if old_quantity == 0 or (old_quantity > 0) == (signed_quantity > 0):
            total_cost = abs(old_quantity) * position["avg_entry_price"] + abs(signed_quantity) * price
            position["avg_entry_price"] = total_cost / abs(new_quantity)
        elif new_quantity == 0:
            del self.positions[order["symbol"]]
        elif (old_quantity > 0) != (new_quantity > 0):
            position["avg_entry_price"] = price
        position["qty"] = new_quantity
        self.cash -= signed_quantity * price
        timestamp = now()
        order.update({
            "status": "filled", "filled_qty": decimal_text(quantity),
            "filled_avg_price": decimal_text(price), "filled_at": timestamp,
            "updated_at": timestamp,
        })

    def cancel(self, order_id: str) -> dict[str, Any]:
        with self.lock:
            order = self.orders.get(order_id)
            if order is None:
                raise ApiError(404, 40410000, "order not found")
            if order["status"] in TERMINAL_ORDER_STATUSES:
                raise ApiError(422, 42210000, "order is already final")
            timestamp = now()
            order.update({"status": "canceled", "canceled_at": timestamp, "updated_at": timestamp})
            response = self.copy(order)
        self.publish_trade_update("canceled", response)
        return response

    def list_orders(self, query: dict[str, list[str]]) -> list[dict[str, Any]]:
        status = query.get("status", ["open"])[0].lower()
        if status not in {"open", "closed", "all"}:
            raise ApiError(422, 42210000, "status must be open, closed, or all")
        limit = int(query.get("limit", ["50"])[0])
        if limit < 1:
            raise ApiError(422, 42210000, "limit must be greater than zero")
        limit = min(limit, 500)
        with self.lock:
            all_orders = list(self.orders.values())
            if status == "open":
                all_orders = [order for order in all_orders if order["status"] in OPEN_ORDER_STATUSES]
            elif status == "closed":
                all_orders = [order for order in all_orders if order["status"] in TERMINAL_ORDER_STATUSES]
            all_orders.sort(key=lambda order: order["submitted_at"], reverse=True)
            return [self.copy(order) for order in all_orders[:limit]]

    def order(self, order_id: str) -> dict[str, Any]:
        with self.lock:
            order = self.orders.get(order_id)
            if order is None:
                raise ApiError(404, 40410000, "order not found")
            return self.copy(order)

    def list_watchlists(self) -> list[dict[str, Any]]:
        with self.lock:
            return [self.copy(value) for value in self.watchlists.values()]

    def watchlist(self, watchlist_id: str) -> dict[str, Any]:
        with self.lock:
            return self.copy(self._watchlist_locked(watchlist_id))

    def create_watchlist(self, payload: dict[str, Any]) -> dict[str, Any]:
        name = str(payload.get("name", "")).strip()
        if not name:
            raise ApiError(422, 42210000, "name is required")
        symbols = self._watchlist_symbols(payload.get("symbols", []))
        with self.lock:
            if any(value["name"] == name for value in self.watchlists.values()):
                raise ApiError(422, 42210000, "watchlist name already exists")
            result = {"id": str(uuid.uuid4()), "name": name, "assets": self._assets(symbols)}
            self.watchlists[result["id"]] = result
            return self.copy(result)

    def replace_watchlist(self, watchlist_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            current = self._watchlist_locked(watchlist_id)
            if "name" in payload:
                name = str(payload["name"]).strip()
                if not name:
                    raise ApiError(422, 42210000, "name must not be empty")
                current["name"] = name
            if "symbols" in payload:
                current["assets"] = self._assets(self._watchlist_symbols(payload["symbols"]))
            if "name" not in payload and "symbols" not in payload:
                raise ApiError(422, 42210000, "name or symbols is required")
            return self.copy(current)

    def add_watchlist_asset(self, watchlist_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        symbols = self._watchlist_symbols([payload.get("symbol")])
        with self.lock:
            current = self._watchlist_locked(watchlist_id)
            existing = [asset["symbol"] for asset in current["assets"]]
            if symbols[0] not in existing:
                current["assets"].extend(self._assets(symbols))
            return self.copy(current)

    def remove_watchlist_asset(self, watchlist_id: str, symbol: str) -> dict[str, Any]:
        with self.lock:
            current = self._watchlist_locked(watchlist_id)
            before = len(current["assets"])
            current["assets"] = [asset for asset in current["assets"] if asset["symbol"] != symbol.upper()]
            if before == len(current["assets"]):
                raise ApiError(404, 40410000, "asset not found in watchlist")
            return self.copy(current)

    def delete_watchlist(self, watchlist_id: str) -> None:
        with self.lock:
            self._watchlist_locked(watchlist_id)
            del self.watchlists[watchlist_id]

    def _watchlist_locked(self, watchlist_id: str) -> dict[str, Any]:
        current = self.watchlists.get(watchlist_id)
        if current is None:
            raise ApiError(404, 40410000, "watchlist not found")
        return current

    @staticmethod
    def _watchlist_symbols(values: Any) -> list[str]:
        if values is None:
            return []
        if not isinstance(values, list):
            raise ApiError(422, 42210000, "symbols must be an array")
        symbols = [str(value or "").upper() for value in values]
        if not all(SYMBOL.fullmatch(symbol) for symbol in symbols):
            raise ApiError(422, 42210000, "invalid watchlist symbol")
        if len(symbols) != len(set(symbols)):
            raise ApiError(422, 42210000, "watchlist contains duplicate symbols")
        return symbols

    @staticmethod
    def _assets(symbols: list[str]) -> list[dict[str, str]]:
        return [{"id": str(uuid.uuid5(uuid.NAMESPACE_URL, "fake-alpaca:" + symbol)), "symbol": symbol,
                 "name": symbol, "status": "active", "tradable": True} for symbol in symbols]

    @staticmethod
    def copy(value: Any) -> Any:
        return json.loads(json.dumps(value, default=decimal_text))

    def publish_trade_update(self, event: str, order: dict[str, Any]) -> None:
        with self.lock:
            peers = list(self.listeners)
            position = self.positions.get(order["symbol"])
            payload = {
                "stream": "trade_updates",
                "data": {
                    "event": event, "at": now(), "timestamp": now(),
                    "event_id": str(uuid.uuid4()), "order": order,
                    "price": order.get("filled_avg_price"), "qty": order.get("filled_qty"),
                    "position_qty": decimal_text(abs(position["qty"])) if position else "0",
                },
            }
        for peer in peers:
            if peer.subscribed:
                try:
                    websocket_write(peer, payload, binary=True)
                except OSError:
                    self.remove_listener(peer)

    def add_listener(self, peer: WebSocketPeer) -> None:
        with self.lock:
            self.listeners.append(peer)

    def remove_listener(self, peer: WebSocketPeer) -> None:
        with self.lock:
            if peer in self.listeners:
                self.listeners.remove(peer)
        try:
            peer.connection.close()
        except OSError:
            pass


class RequestHandler(BaseHTTPRequestHandler):
    server: "FakeAlpacaServer"
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args: Any) -> None:
        LOG.info("%s - %s", self.address_string(), format % args)

    def log_request(self, code: int | str = "-", size: int | str = "-") -> None:
        """Keep probe traffic out of application logs; retain real API access logs."""
        if urlparse(self.path).path == "/healthz":
            return
        super().log_request(code, size)

    def do_GET(self) -> None:
        if self.path.split("?", 1)[0] == "/stream" and self.headers.get("Upgrade", "").lower() == "websocket":
            self.server.websocket(self)
            return
        self.dispatch()

    def do_POST(self) -> None:
        self.dispatch()

    def do_PUT(self) -> None:
        self.dispatch()

    def do_DELETE(self) -> None:
        self.dispatch()

    def dispatch(self) -> None:
        request_id = str(uuid.uuid4())
        try:
            route, query = self.route()
            if route == "/healthz":
                return self.respond(200, {"status": "ok"}, request_id)
            if route.startswith("/__mock/"):
                return self.mock_control(route, request_id)
            self.require_auth()
            if route == "/v2/account" and self.command == "GET":
                return self.respond(200, self.server.state.account(), request_id)
            if route == "/v2/positions" and self.command == "GET":
                return self.respond(200, self.server.state.position_list(), request_id)
            if route == "/v2/orders":
                if self.command == "GET":
                    return self.respond(200, self.server.state.list_orders(query), request_id)
                if self.command == "POST":
                    return self.respond(200, self.server.state.create_order(self.body()), request_id)
            if route.startswith("/v2/orders/"):
                order_id = unquote(route.removeprefix("/v2/orders/"))
                if self.command == "GET":
                    return self.respond(200, self.server.state.order(order_id), request_id)
                if self.command == "DELETE":
                    self.server.state.cancel(order_id)
                    return self.respond(204, None, request_id)
            if route == "/v2/watchlists":
                if self.command == "GET":
                    return self.respond(200, self.server.state.list_watchlists(), request_id)
                if self.command == "POST":
                    return self.respond(200, self.server.state.create_watchlist(self.body()), request_id)
            if route.startswith("/v2/watchlists/"):
                remainder = route.removeprefix("/v2/watchlists/")
                segments = remainder.split("/")
                watchlist_id = unquote(segments[0])
                if len(segments) == 1:
                    if self.command == "GET":
                        return self.respond(200, self.server.state.watchlist(watchlist_id), request_id)
                    if self.command == "PUT":
                        return self.respond(200, self.server.state.replace_watchlist(watchlist_id, self.body()), request_id)
                    if self.command == "POST":
                        return self.respond(200, self.server.state.add_watchlist_asset(watchlist_id, self.body()), request_id)
                    if self.command == "DELETE":
                        self.server.state.delete_watchlist(watchlist_id)
                        return self.respond(204, None, request_id)
                if len(segments) == 2 and self.command == "DELETE":
                    return self.respond(200, self.server.state.remove_watchlist_asset(watchlist_id, unquote(segments[1])), request_id)
            raise ApiError(404, 40410000, "endpoint not found")
        except ApiError as error:
            self.respond(error.status, {"code": error.code, "message": error.message}, request_id)
        except ValueError as error:
            self.respond(422, {"code": 42210000, "message": str(error)}, request_id)
        except Exception:
            LOG.exception("Unhandled request failure")
            self.respond(500, {"code": 50010000, "message": "internal mock server error"}, request_id)

    def route(self) -> tuple[str, dict[str, list[str]]]:
        parsed = urlparse(self.path)
        return parsed.path.rstrip("/") or "/", parse_qs(parsed.query)

    def require_auth(self) -> None:
        if not self.server.require_auth:
            return
        key = self.headers.get("APCA-API-KEY-ID")
        secret = self.headers.get("APCA-API-SECRET-KEY")
        if not key or not secret or (self.server.api_key and key != self.server.api_key) or (self.server.api_secret and secret != self.server.api_secret):
            raise ApiError(401, 40110000, "invalid API key")

    def body(self) -> dict[str, Any]:
        transfer_encoding = self.headers.get("Transfer-Encoding", "").lower()
        if "chunked" in transfer_encoding:
            raw = self.chunked_body()
        else:
            length = int(self.headers.get("Content-Length", "0"))
            if length > MAX_REQUEST_BODY_BYTES:
                raise ApiError(413, 41310000, "request body is too large")
            raw = self.rfile.read(length) if length else b"{}"
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as error:
            raise ApiError(400, 40010000, "invalid JSON request body") from error
        if not isinstance(value, dict):
            raise ApiError(422, 42210000, "JSON body must be an object")
        return value

    def chunked_body(self) -> bytes:
        """Decode an HTTP/1.1 chunked request body (used by Spring clients)."""
        chunks: list[bytes] = []
        total = 0
        while True:
            size_line = self.rfile.readline(8192)
            if not size_line:
                raise ApiError(400, 40010000, "incomplete chunked request body")
            try:
                # Chunk extensions are permitted, but irrelevant for this API.
                size = int(size_line.split(b";", 1)[0].strip(), 16)
            except ValueError as error:
                raise ApiError(400, 40010000, "invalid chunked request body") from error
            if size < 0:
                raise ApiError(400, 40010000, "invalid chunked request body")
            if total + size > MAX_REQUEST_BODY_BYTES:
                raise ApiError(413, 41310000, "request body is too large")
            if size == 0:
                # Consume optional trailer fields and their terminating empty line;
                # otherwise they would be parsed as a new HTTP request.
                while True:
                    trailer = self.rfile.readline(8192)
                    if trailer in {b"", b"\r\n", b"\n"}:
                        return b"".join(chunks)
            chunk = self.rfile.read(size)
            if len(chunk) != size or self.rfile.read(2) != b"\r\n":
                raise ApiError(400, 40010000, "incomplete chunked request body")
            chunks.append(chunk)
            total += size

    def mock_control(self, route: str, request_id: str) -> None:
        # Test-only control plane. It is deliberately separate from Alpaca paths.
        if route == "/__mock/state" and self.command == "GET":
            return self.respond(200, {
                "account": self.server.state.account(), "orders": self.server.state.list_orders({"status": ["all"], "limit": ["500"]}),
                "positions": self.server.state.position_list(), "watchlists": self.server.state.list_watchlists(),
            }, request_id)
        if route == "/__mock/reset" and self.command == "POST":
            self.server.state.reset()
            return self.respond(200, {"status": "reset"}, request_id)
        if route == "/__mock/seed" and self.command == "POST":
            payload = self.body()
            self.server.state.seed(payload.get("cash"), payload.get("positions", []))
            return self.respond(200, {
                "account": self.server.state.account(),
                "positions": self.server.state.position_list(),
            }, request_id)
        if route == "/__mock/prices" and self.command == "POST":
            payload = self.body()
            with self.server.state.lock:
                for symbol, value in payload.items():
                    normalized = str(symbol).upper()
                    if not SYMBOL.fullmatch(normalized):
                        raise ApiError(422, 42210000, "invalid price symbol")
                    self.server.state.prices[normalized] = as_decimal(value, "price")
            return self.respond(200, {"prices": {key: decimal_text(value) for key, value in self.server.state.prices.items()}}, request_id)
        if route.startswith("/__mock/fill/") and self.command == "POST":
            order_id = route.removeprefix("/__mock/fill/")
            payload = self.body()
            fill_price = as_decimal(payload["price"], "price") if "price" in payload else None
            return self.respond(200, self.server.state.fill(order_id, fill_price), request_id)
        raise ApiError(404, 40410000, "mock control endpoint not found")

    def respond(self, status: int, payload: Any, request_id: str) -> None:
        self.send_response(status)
        self.send_header("X-Request-ID", request_id)
        self.send_header("Cache-Control", "no-store")
        if status == HTTPStatus.NO_CONTENT:
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        encoded = json.dumps(payload, separators=(",", ":"), default=decimal_text).encode("utf-8")
        self.send_header("Content-Type", JSON_TYPE)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


class FakeAlpacaServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, host: str, port: int, state: PaperAccount, require_auth: bool, api_key: str | None, api_secret: str | None) -> None:
        super().__init__((host, port), RequestHandler)
        self.state = state
        self.require_auth = require_auth
        self.api_key = api_key
        self.api_secret = api_secret

    def websocket(self, handler: RequestHandler) -> None:
        key = handler.headers.get("Sec-WebSocket-Key")
        if not key:
            handler.send_error(400, "Sec-WebSocket-Key is required")
            return
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")).digest()).decode("ascii")
        handler.send_response(101, "Switching Protocols")
        handler.send_header("Upgrade", "websocket")
        handler.send_header("Connection", "Upgrade")
        handler.send_header("Sec-WebSocket-Accept", accept)
        handler.end_headers()
        handler.wfile.flush()
        peer = WebSocketPeer(handler.connection)
        self.state.add_listener(peer)
        try:
            while True:
                opcode, payload = websocket_read(handler.rfile)
                if opcode is None or opcode == 8:
                    return
                if opcode == 9:
                    websocket_write_raw(peer, payload, 10)
                    continue
                if opcode not in {1, 2}:
                    continue
                try:
                    message = json.loads(payload.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    continue
                self.websocket_message(peer, message)
        except (ConnectionError, OSError):
            return
        finally:
            self.state.remove_listener(peer)

    def websocket_message(self, peer: WebSocketPeer, message: dict[str, Any]) -> None:
        action = message.get("action")
        if action == "auth":
            valid = not self.require_auth or (
                bool(message.get("key")) and bool(message.get("secret"))
                and (not self.api_key or message.get("key") == self.api_key)
                and (not self.api_secret or message.get("secret") == self.api_secret)
            )
            peer.authenticated = valid
            websocket_write(peer, {"stream": "authorization", "data": {
                "status": "authorized" if valid else "unauthorized", "action": "authenticate",
            }}, binary=True)
            return
        if action == "listen":
            streams = message.get("data", {}).get("streams", [])
            if not peer.authenticated:
                websocket_write(peer, {"stream": "authorization", "data": {"status": "unauthorized", "action": "listen"}}, binary=True)
                return
            peer.subscribed = "trade_updates" in streams
            websocket_write(peer, {"stream": "listening", "data": {
                "streams": ["trade_updates"] if peer.subscribed else [],
            }}, binary=True)


def websocket_read(stream: Any) -> tuple[int | None, bytes]:
    header = stream.read(2)
    if len(header) != 2:
        return None, b""
    opcode = header[0] & 0x0F
    masked = header[1] & 0x80
    size = header[1] & 0x7F
    if size == 126:
        size = struct.unpack("!H", read_exact(stream, 2))[0]
    elif size == 127:
        size = struct.unpack("!Q", read_exact(stream, 8))[0]
    if size > 1_048_576:
        raise ConnectionError("WebSocket message is too large")
    mask = read_exact(stream, 4) if masked else b""
    payload = bytearray(read_exact(stream, size))
    if masked:
        for index in range(size):
            payload[index] ^= mask[index % 4]
    return opcode, bytes(payload)


def read_exact(stream: Any, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise ConnectionError("unexpected EOF")
    return data


def websocket_write(peer: WebSocketPeer, payload: Any, binary: bool) -> None:
    websocket_write_raw(peer, json.dumps(payload, separators=(",", ":")).encode("utf-8"), 2 if binary else 1)


def websocket_write_raw(peer: WebSocketPeer, payload: bytes, opcode: int) -> None:
    size = len(payload)
    if size < 126:
        header = bytes((0x80 | opcode, size))
    elif size <= 0xFFFF:
        header = bytes((0x80 | opcode, 126)) + struct.pack("!H", size)
    else:
        header = bytes((0x80 | opcode, 127)) + struct.pack("!Q", size)
    with peer.lock:
        peer.connection.sendall(header + payload)


def parse_prices(values: list[str]) -> dict[str, Decimal]:
    result: dict[str, Decimal] = {"AAPL": Decimal("100"), "MSFT": Decimal("100"), "SPY": Decimal("100")}
    for value in values:
        symbol, separator, price = value.partition("=")
        symbol = symbol.upper()
        if not separator or not SYMBOL.fullmatch(symbol):
            raise argparse.ArgumentTypeError("--price must have the form SYMBOL=PRICE")
        try:
            result[symbol] = as_decimal(price, "price")
        except ApiError as error:
            raise argparse.ArgumentTypeError(error.message) from error
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="In-memory mock of Alpaca's Paper Trading API")
    parser.add_argument("--host", default=os.getenv("FAKE_ALPACA_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.getenv("FAKE_ALPACA_PORT", "8080")))
    parser.add_argument("--fill-mode", choices=("immediate", "manual"), default=os.getenv("FAKE_ALPACA_FILL_MODE", "immediate"),
                        help="fill marketable market/limit orders immediately, or leave every order open")
    parser.add_argument("--price", action="append", default=[], metavar="SYMBOL=PRICE", help="set a deterministic symbol price; repeatable")
    parser.add_argument("--require-auth", action="store_true", default=os.getenv("FAKE_ALPACA_REQUIRE_AUTH", "").lower() in {"1", "true", "yes"})
    parser.add_argument("--api-key", default=os.getenv("ALPACA_API_KEY"))
    parser.add_argument("--api-secret", default=os.getenv("ALPACA_API_SECRET"))
    parser.add_argument("--verbose", action="store_true")
    options = parser.parse_args()
    logging.basicConfig(level=logging.DEBUG if options.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    server = FakeAlpacaServer(options.host, options.port, PaperAccount(options.fill_mode, parse_prices(options.price)),
                              options.require_auth, options.api_key, options.api_secret)
    LOG.info("Fake Alpaca Paper API listening at http://%s:%s (fill-mode=%s, auth=%s)", options.host, options.port, options.fill_mode, options.require_auth)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        LOG.info("Stopping fake Alpaca API")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
