import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { demoCities, type City } from "../../../packages/protocol/src/index.ts";
import { setDemoEnabled } from "./api.ts";

export interface GeoPoint {
  lat: number;
  lng: number;
}

export interface FavEntry {
  lineId: string;
  lineName: string;
  startSn: string;
  endSn: string;
  cityId: string;
  addedAt: number;
}

export type Page =
  | { kind: "near" }
  | { kind: "search" }
  | { kind: "fav" }
  | { kind: "settings" }
  | { kind: "line"; lineId: string; lineName: string; startSn: string; endSn: string; cityId: string }
  | { kind: "station"; physicalStId: string; name: string; cityId: string };

const DEFAULT_GEO: GeoPoint = { lat: 24.813761, lng: 113.591965 };
const LS = {
  city: "chelaile.city",
  geo: "chelaile.geo",
  favs: "chelaile.favs",
  demo: "chelaile.demo"
};

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function parseHash(hash: string): Page {
  const clean = hash.replace(/^#\/?/, "");
  const [path, query] = clean.split("?");
  const params = new URLSearchParams(query ?? "");
  const parts = path.split("/").filter(Boolean);
  if (parts[0] === "line" && parts[1]) {
    return {
      kind: "line",
      lineId: decodeURIComponent(parts[1]),
      lineName: params.get("name") ?? "",
      startSn: params.get("start") ?? "",
      endSn: params.get("end") ?? "",
      cityId: params.get("cityId") ?? ""
    };
  }
  if (parts[0] === "station" && parts[1]) {
    return {
      kind: "station",
      physicalStId: decodeURIComponent(parts[1]),
      name: params.get("name") ?? "",
      cityId: params.get("cityId") ?? ""
    };
  }
  if (parts[0] === "search") return { kind: "search" };
  if (parts[0] === "fav") return { kind: "fav" };
  if (parts[0] === "settings") return { kind: "settings" };
  return { kind: "near" };
}

export function toHash(page: Page): string {
  switch (page.kind) {
    case "line": {
      const q = new URLSearchParams({ name: page.lineName, start: page.startSn, end: page.endSn, cityId: page.cityId });
      return "#/line/" + encodeURIComponent(page.lineId) + "?" + q.toString();
    }
    case "station": {
      const q = new URLSearchParams({ name: page.name, cityId: page.cityId });
      return "#/station/" + encodeURIComponent(page.physicalStId) + "?" + q.toString();
    }
    case "search":
      return "#/search";
    case "fav":
      return "#/fav";
    case "settings":
      return "#/settings";
    default:
      return "#/near";
  }
}

interface StoreValue {
  page: Page;
  navigate: (page: Page) => void;
  back: () => void;
  cities: City[];
  city: City | null;
  setCity: (city: City) => void;
  geo: GeoPoint;
  setGeo: (geo: GeoPoint) => void;
  locate: () => Promise<string | null>;
  locating: boolean;
  favorites: FavEntry[];
  isFavorite: (lineId: string) => boolean;
  toggleFavorite: (entry: Omit<FavEntry, "addedAt">) => void;
  demoMode: boolean;
  setDemoMode: (on: boolean) => void;
  refreshTick: number;
  refresh: () => void;
  toast: string | null;
  showToast: (message: string) => void;
}

const StoreContext = createContext<StoreValue | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const [page, setPage] = useState<Page>(() => parseHash(location.hash));
  const [history, setHistory] = useState<Page[]>([]);
  const [cities, setCities] = useState<City[]>([]);
  const [city, setCityState] = useState<City | null>(() => readJson<City | null>(LS.city, null));
  const [geo, setGeoState] = useState<GeoPoint>(() => readJson<GeoPoint>(LS.geo, DEFAULT_GEO));
  const [favorites, setFavorites] = useState<FavEntry[]>(() => readJson<FavEntry[]>(LS.favs, []));
  const [demoMode, setDemoModeState] = useState<boolean>(() => readJson<boolean>(LS.demo, false));
  const [locating, setLocating] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const [toast, setToast] = useState<string | null>(null);

  // hash 路由：浏览器前进/后退也能工作
  useEffect(() => {
    const onHash = () => setPage(parseHash(location.hash));
    window.addEventListener("hashchange", onHash);
    if (!location.hash) location.replace(toHash({ kind: "near" }));
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  const navigate = useCallback((next: Page) => {
    setHistory((h) => [...h.slice(-20), page]);
    location.hash = toHash(next);
    setPage(next);
    window.scrollTo({ top: 0 });
  }, [page]);

  const back = useCallback(() => {
    setHistory((h) => {
      if (h.length === 0) {
        location.hash = toHash({ kind: "near" });
        return h;
      }
      const prev = h[h.length - 1];
      location.hash = toHash(prev);
      setPage(prev);
      return h.slice(0, -1);
    });
  }, []);

  const setCity = useCallback((next: City) => {
    setCityState(next);
    localStorage.setItem(LS.city, JSON.stringify(next));
  }, []);

  const setGeo = useCallback((next: GeoPoint) => {
    setGeoState(next);
    localStorage.setItem(LS.geo, JSON.stringify(next));
  }, []);

  const setDemoMode = useCallback((on: boolean) => {
    setDemoModeState(on);
    setDemoEnabled(on);
    localStorage.setItem(LS.demo, JSON.stringify(on));
  }, []);

  // 首屏同步演示模式开关（localStorage 里可能已开启）
  useEffect(() => {
    setDemoEnabled(demoMode);
  }, [demoMode]);

  const toggleFavorite = useCallback((entry: Omit<FavEntry, "addedAt">) => {
    setFavorites((prev) => {
      const exists = prev.some((f) => f.lineId === entry.lineId);
      const next = exists ? prev.filter((f) => f.lineId !== entry.lineId) : [...prev, { ...entry, addedAt: Date.now() }];
      localStorage.setItem(LS.favs, JSON.stringify(next));
      return next;
    });
  }, []);

  const showToast = useCallback((message: string) => {
    setToast(message);
    window.setTimeout(() => setToast((cur) => (cur === message ? null : cur)), 2400);
  }, []);

  const locate = useCallback(async () => {
    setLocating(true);
    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        if (!navigator.geolocation) {
          reject(new Error("浏览器不支持定位"));
          return;
        }
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 8000, maximumAge: 60_000 });
      });
      const next = { lat: position.coords.latitude, lng: position.coords.longitude };
      setGeo(next);
      const result = await fetch("/api/locate?" + new URLSearchParams({ lat: String(next.lat), lng: String(next.lng) }))
        .then((r) => r.json())
        .catch(() => null);
      const name = result?.data?.name ?? null;
      const matched: City | null = result?.data?.city ?? null;
      if (matched) setCity(matched);
      return name;
    } catch (err) {
      showToast(err instanceof Error ? err.message : "定位失败");
      return null;
    } finally {
      setLocating(false);
    }
  }, [setCity, setGeo, showToast]);

  const value = useMemo<StoreValue>(
    () => ({
      page,
      navigate,
      back,
      cities,
      city,
      setCity,
      geo,
      setGeo,
      locate,
      locating,
      favorites,
      isFavorite: (lineId: string) => favorites.some((f) => f.lineId === lineId),
      toggleFavorite,
      demoMode,
      setDemoMode,
      refreshTick,
      refresh: () => setRefreshTick((n) => n + 1),
      toast,
      showToast
    }),
    [page, navigate, back, cities, city, setCity, geo, setGeo, locate, locating, favorites, toggleFavorite, demoMode, setDemoMode, refreshTick, toast, showToast]
  );

  // 城市列表只拉一次
  useEffect(() => {
    if (demoMode) {
      setCities(demoCities);
      if (!city) setCity(city ?? demoCities[0]);
      return;
    }
    let alive = true;
    fetch("/api/cities")
      .then((r) => r.json())
      .then((body) => {
        if (!alive) return;
        const list: City[] = body?.data ?? [];
        setCities(list);
        if (!city && list.length) {
          const fallback = list.find((c) => c.cityId === "241") ?? list[0];
          setCity(fallback);
        }
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [city, setCity, setCities, demoMode]);

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(): StoreValue {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error("useStore 必须在 StoreProvider 内使用");
  return ctx;
}