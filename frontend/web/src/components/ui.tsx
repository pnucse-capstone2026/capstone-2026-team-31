import { Link } from "react-router-dom";
export const Money = ({ value }: { value?: string }) => <>{value == null ? "—" : new Intl.NumberFormat(undefined, { style: "currency", currency: "USD" }).format(Number(value))}</>;
export const DateTime = ({ value }: { value?: string }) => <>{value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—"}</>;
export function AppShell({ children }: { children: React.ReactNode }) { return <div className="shell"><header><Link className="brand" to="/dashboard">Tradify <small>paper</small></Link><nav><Link to="/dashboard">Dashboard</Link><Link to="/watchlists">Watchlists</Link></nav><span className="paper">SINGLE PAPER ACCOUNT</span></header><main>{children}</main></div>; }
export const Loading = () => <div className="panel">Loading account data…</div>;
export const ErrorPanel = ({ message }: { message: string }) => <div className="panel error">{message}</div>;
