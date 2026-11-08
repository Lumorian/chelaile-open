import { ChelaileClient, type City } from "../../protocol/src/index.ts";
import { TtlCache } from "./cache.ts";

/** 各链路的缓存时长：实时类 15s，静态类 30min~6h。 */
export const TTL = {
  cities: 6 * 60 * 60_000,
  cityLines: 30 * 60_000,
  route: 30 * 60_000,
  near: 15_000,
  station: 15_000,
  detail: 15_000
} as const;

export interface RequestContext {
  client: ChelaileClient;
  cache: TtlCache;
}

export interface ApiResult<T> {
  data: T;
  cached: boolean;
  fetchedAt: string;
}

async function wrap<T>(ctx: RequestContext, key: string, ttl: number, loader: () => Promise<T>): Promise<ApiResult<T>> {
  const before = ctx.cache.get<T>(key);
  const data = await ctx.cache.wrap(key, ttl, loader);
  return { data, cached: before !== undefined, fetchedAt: new Date().toISOString() };
}

/** 城市列表 + 名称索引（name → cityId）。 */
export async function getCities(ctx: RequestContext): Promise<ApiResult<City[]>> {
  return wrap(ctx, "cities", TTL.cities, () => ctx.client.listCities());
}

export async function findCityByName(ctx: RequestContext, name: string): Promise<City | undefined> {
  const { data } = await getCities(ctx);
  const trimmed = name.trim();
  return data.find((c) => c.cityName === trimmed) ?? data.find((c) => c.cityName.includes(trimmed));
}

export async function locateCity(ctx: RequestContext, lat: number, lng: number) {
  return wrap(ctx, `locate:${lat.toFixed(3)},${lng.toFixed(3)}`, TTL.cities, async () => {
    const name = await ctx.client.locateCity({ lat, lng });
    if (!name) return { name: null, city: null };
    const city = await findCityByName(ctx, name);
    return { name, city: city ?? null };
  });
}

export async function searchLines(ctx: RequestContext, cityId: string, keyword: string, limit = 40) {
  return wrap(ctx, `lines:${cityId}:${keyword}:${limit}`, TTL.cityLines, async () => {
    const all = await ctx.client.listCityLines(cityId);
    const kw = keyword.trim().toLowerCase();
    const matched = !kw
      ? all
      : all.filter((l) => l.lineName.toLowerCase().includes(kw) || (l.startStopName + l.endStopName).toLowerCase().includes(kw));
    return matched
      .sort((a, b) => {
        const aExact = a.lineName.toLowerCase() === kw ? 0 : 1;
        const bExact = b.lineName.toLowerCase() === kw ? 0 : 1;
        if (aExact !== bExact) return aExact - bExact;
        return a.lineName.length - b.lineName.length || a.lineName.localeCompare(b.lineName, "zh-CN", { numeric: true });
      })
      .slice(0, limit);
  });
}

export async function lineRoute(ctx: RequestContext, lineId: string, cityId: string) {
  return wrap(ctx, `route:${cityId}:${lineId}`, TTL.route, () => ctx.client.getLineRoute(lineId, cityId));
}

export async function nearbyLines(ctx: RequestContext, cityId: string, lat: number, lng: number) {
  return wrap(ctx, `near:${cityId}:${lat.toFixed(4)},${lng.toFixed(4)}`, TTL.near, () =>
    ctx.client.getNearbyLines({ lat, lng }, cityId)
  );
}

export async function nearbyStations(ctx: RequestContext, cityId: string, lat: number, lng: number) {
  return wrap(ctx, `nearst:${cityId}:${lat.toFixed(4)},${lng.toFixed(4)}`, TTL.near, () =>
    ctx.client.getNearbyStations({ lat, lng }, cityId)
  );
}

export async function stationDetail(ctx: RequestContext, cityId: string, physicalStId: string, stnName: string) {
  return wrap(ctx, `station:${physicalStId}:${stnName}`, TTL.station, () =>
    ctx.client.getStationDetail(physicalStId, stnName, cityId)
  );
}

export interface LineDetailParams {
  lineId: string;
  lineName: string;
  direction: string;
  stationName: string;
  nextStationName: string;
  lineNo?: string;
  targetOrder: string;
}

export async function lineDetail(ctx: RequestContext, cityId: string, p: LineDetailParams) {
  return wrap(ctx, `detail:${p.lineId}:${p.targetOrder}`, TTL.detail, () =>
    ctx.client.getLineDetail({ ...p, cityId })
  );
}
