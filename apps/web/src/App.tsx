import { StoreProvider, useStore } from "./store.tsx";
import { NearbyView } from "./views/NearbyView.tsx";
import { SearchView } from "./views/SearchView.tsx";
import { LineView } from "./views/LineView.tsx";
import { StationView } from "./views/StationView.tsx";
import { FavoritesView } from "./views/FavoritesView.tsx";
import { SettingsView } from "./views/SettingsView.tsx";
import { CitySheet } from "./components/CitySheet.tsx";
import { BackIcon, BusIcon, GearIcon, SearchIcon, StarIcon } from "./components/Icons.tsx";
import { useState } from "react";

function Shell() {
  const { page, navigate, back, city, toast } = useStore();
  const [sheet, setSheet] = useState(false);
  const isDetail = page.kind === "line" || page.kind === "station";
  const tab = isDetail ? "" : page.kind;

  return (
    <div className="app">
      <header className="topbar">
        {isDetail ? (
          <button className="icon-btn" onClick={back} aria-label="返回">
            <BackIcon />
          </button>
        ) : null}
        <h1>
          {page.kind === "line"
            ? page.lineName || "线路详情"
            : page.kind === "station"
              ? "站点详情"
              : page.kind === "search"
                ? "搜索"
                : page.kind === "fav"
                  ? "我的关注"
                  : page.kind === "settings"
                    ? "设置"
                    : "车来了 · 开放版"}
        </h1>
        {!isDetail ? (
          <button className="city-btn" onClick={() => setSheet(true)}>
            {city?.cityName ?? "选择城市"} ▾
          </button>
        ) : null}
      </header>

      <main className="content">
        {page.kind === "near" ? <NearbyView /> : null}
        {page.kind === "search" ? <SearchView /> : null}
        {page.kind === "line" ? <LineView page={page} /> : null}
        {page.kind === "station" ? <StationView page={page} /> : null}
        {page.kind === "fav" ? <FavoritesView /> : null}
        {page.kind === "settings" ? <SettingsView /> : null}
      </main>

      <nav className="tabbar">
        <button className={tab === "near" ? "active" : ""} onClick={() => navigate({ kind: "near" })}>
          <span className="glyph"><BusIcon /></span>
          附近
        </button>
        <button className={tab === "search" ? "active" : ""} onClick={() => navigate({ kind: "search" })}>
          <span className="glyph"><SearchIcon /></span>
          搜索
        </button>
        <button className={tab === "fav" ? "active" : ""} onClick={() => navigate({ kind: "fav" })}>
          <span className="glyph"><StarIcon filled={tab === "fav"} /></span>
          关注
        </button>
        <button className={tab === "settings" ? "active" : ""} onClick={() => navigate({ kind: "settings" })}>
          <span className="glyph"><GearIcon /></span>
          设置
        </button>
      </nav>

      <CitySheet open={sheet} onClose={() => setSheet(false)} />
      {toast ? <div className="toast">{toast}</div> : null}
    </div>
  );
}

export default function App() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  );
}