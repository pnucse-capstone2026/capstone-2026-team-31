import { BrowserRouter, Route, Routes } from "react-router-dom";
import { DashboardPage, NotFoundPage, WatchlistDetailPage, WatchlistsPage } from "./pages/Pages";
export default function App() { return <BrowserRouter><Routes><Route path="/" element={<DashboardPage />} /><Route path="/dashboard" element={<DashboardPage />} /><Route path="/watchlists" element={<WatchlistsPage />} /><Route path="/watchlists/:watchlistId" element={<WatchlistDetailPage />} /><Route path="*" element={<NotFoundPage />} /></Routes></BrowserRouter>; }
