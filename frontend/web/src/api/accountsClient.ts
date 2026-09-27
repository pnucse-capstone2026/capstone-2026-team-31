export class ApiError extends Error { constructor(public status: number, public payload: unknown) { super(`API Error ${status}`); } }
export const errorText = (error: any) => error?.payload?.message || error?.message || "An unexpected error occurred.";
const base = () => import.meta.env.VITE_ACCOUNTS_API_BASE_URL || "http://localhost:8081";
async function request(path: string, init?: RequestInit) {
  const response = await fetch(base() + path, { ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  if (!response.ok) { let body: unknown; try { body = await response.json(); } catch { body = await response.text(); } throw new ApiError(response.status, body); }
  if (response.status === 204) return undefined;
  return response.json();
}
export const accounts = {
  dashboard: () => request("/api/v1/dashboard"), account: () => request("/api/v1/account"), orders: () => request("/api/v1/orders"),
  reconcile: () => request("/api/v1/account/reconcile", { method: "POST" }),
  watchlists: () => request("/api/v1/watchlists"), watchlist: (id: string) => request(`/api/v1/watchlists/${id}`),
  createWatchlist: (name: string, symbols: string[]) => request("/api/v1/watchlists", { method: "POST", body: JSON.stringify({ name, symbols }) }),
  addTicker: (id: string, ticker: string) => request(`/api/v1/watchlists/${id}/entries`, { method: "POST", body: JSON.stringify({ ticker }) }),
  toggleTicker: (id: string, ticker: string, isActive: boolean) => request(`/api/v1/watchlists/${id}/entries/${ticker}`, { method: "PATCH", body: JSON.stringify({ isActive }) }),
  removeTicker: (id: string, ticker: string) => request(`/api/v1/watchlists/${id}/entries/${ticker}`, { method: "DELETE" }),
  deleteWatchlist: (id: string) => request(`/api/v1/watchlists/${id}`, { method: "DELETE" }),
};
