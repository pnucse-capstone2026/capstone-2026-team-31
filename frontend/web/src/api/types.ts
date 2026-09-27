export type Freshness = "FRESH" | "STALE" | "UNAVAILABLE";
export interface Account { accountNumber?: string; cash?: string; buyingPower?: string; portfolioValue?: string; equity?: string; lastSyncedAt?: string; status?: string; }
export interface Position { symbol: string; quantity?: string; averageEntryPrice?: string; currentPrice?: string; marketValue?: string; unrealizedPnl?: string; }
export interface Order { symbol?: string; side?: string; quantity?: string; filledQuantity?: string; status?: string; updatedAt?: string; }
export interface Dashboard { account?: Account; positions: Position[]; asOf?: string; freshness: Freshness; }
export interface WatchlistEntry { ticker: string; active: boolean; }
export interface Watchlist { watchlist: { watchlistId: string; name: string; syncStatus?: string }; entries: WatchlistEntry[]; }
