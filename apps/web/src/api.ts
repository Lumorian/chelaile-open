import {
  demoCities,
  demoLineDetail,
  demoLineRoute,
  demoNearLines,
  demoNearStations,
  demoSearchLines,
  demoStationDetail,
  type City,
  type LineDetailData,
  type LineRouteData,
  type LineSummary,
  type NearLinesData,
  type NearStationsData,
  type StationDetailData
} from "../../../packages/protocol/src/index.ts";

export interface Envelope<T> {
  ok: boolean;
  data: T;
  cached?: boolean;
  fetchedAt?: string;
  error?: string;
}

export class ApiClientError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
  }
}

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? "";

let demoEnabled = false;

/** 演示模式：所有请求走内置样例数据，不发网络请求。 */
export function setDemoEnabled(on: boolean) {
  demoEnabled = on;
}

export function isDemoEnabled(): boolean {
  return demoEnabled;
}

const later = <T>(value: T): Promise<T> => new Promise((resolve) => window.setTimeout(() => resolve(value), 180));

async function get<T>(path: string, params: Record<string, string | number | undefined> = {}): Promise<T> {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") qs.set(k, String(v));
  }
  const url = BASE + path + (qs.toString() ? "?" + qs.toString() : "");
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  const text = await res.text();
  let body: Envelope<T>;
  try {
    body = JSON.parse(text) as Envelope<T>;
  } catch {
    throw new ApiClientError("服务返回了非 JSON 内容（" + res.status + "）", res.status);
  }
  if (!res.ok || body.ok === false) throw new ApiClientError(body.error ?? "请求失败", res.status);
  return body.data;
}

export interface LocateResult {
  name: string | null;
  city: City | null;
}

export const api = {
  health: () => get<{ status: string; upstream: string; time: string }>("/api/health"),

  cities: () => (demoEnabled ? later(demoCities) : get<City[]>("/api/cities")),

  locate: (lat: number, lng: number) =>
    demoEnabled ? later<LocateResult>({ name: "韶关", city: demoCities[0] }) : get<LocateResult>("/api/locate", { lat, lng }),

  searchLines: (cityId: string, q: string, limit = 40) =>
    demoEnabled ? later(demoSearchLines(q)) : get<LineSummary[]>("/api/lines", { cityId, q, limit }),

  lineRoute: (lineId: string, cityId: string) =>
    demoEnabled ? later(demoLineRoute(lineId)) : get<LineRouteData>("/api/line/" + encodeURIComponent(lineId) + "/route", { cityId }),

  lineDetail: (p: {
    lineId: string;
    lineName: string;
    direction: number | string;
    stationName: string;
    nextStationName: string;
    lineNo?: string;
    targetOrder: number | string;
    cityId: string;
  }) =>
    demoEnabled
      ? later(demoLineDetail(p.lineId, Number(p.targetOrder)))
      : get<LineDetailData>("/api/line/" + encodeURIComponent(p.lineId) + "/detail", { ...p }),

  nearbyLines: (cityId: string, lat: number, lng: number) =>
    demoEnabled ? later(demoNearLines()) : get<NearLinesData>("/api/nearby/lines", { cityId, lat, lng }),

  nearbyStations: (cityId: string, lat: number, lng: number) =>
    demoEnabled ? later(demoNearStations()) : get<NearStationsData>("/api/nearby/stations", { cityId, lat, lng }),

  stationDetail: (physicalStId: string, name: string, cityId: string) =>
    demoEnabled
      ? later(demoStationDetail(physicalStId, name))
      : get<StationDetailData>("/api/station/" + encodeURIComponent(physicalStId), { name, cityId })
};
