import { AES_KEY, CITY_PARAMS, DEFAULT_BASE_URL, H5_PARAMS, SIGN_SALT } from "./config.ts";
import { aesEcbDecrypt } from "./aes.ts";
import { stripWrapper } from "./envelope.ts";
import { getText, type RequestOptions } from "./http.ts";
import { md5Hex } from "./md5.ts";
import type {
  City,
  LineDetailData,
  LineRouteData,
  LineSummary,
  NearLinesData,
  NearStationsData,
  StationDetailData
} from "./types.ts";

export interface ClientOptions {
  baseUrl?: string;
  timeoutMs?: number;
  retries?: number;
  /** 设备/浏览器标识，H5 端用来区分收藏等，随机即可。 */
  userId?: string;
  cityId?: string;
  lat?: number;
  lng?: number;
}

export class ApiError extends Error {
  readonly status: string;
  readonly endpoint: string;
  constructor(status: string, endpoint: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.endpoint = endpoint;
  }
}

export interface GeoPoint {
  lat: number;
  lng: number;
}

const BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

export function base64ToBytes(b64: string): Uint8Array {
  const clean = b64.replace(/[\r\n\s]/g, "");
  const out = new Uint8Array(Math.floor((clean.length * 3) / 4));
  let buffer = 0;
  let bits = 0;
  let idx = 0;
  for (let i = 0; i < clean.length; i++) {
    const ch = clean[i];
    if (ch === "=") break;
    const v = BASE64_ALPHABET.indexOf(ch);
    if (v < 0) continue;
    buffer = (buffer << 6) | v;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[idx++] = (buffer >> bits) & 0xff;
    }
  }
  return out.subarray(0, idx);
}

export function bytesToUtf8(bytes: Uint8Array): string {
  return new TextDecoder("utf-8").decode(bytes);
}

function randomUserId(): string {
  const rnd = Math.random().toString(36).slice(2, 10);
  return "browser_" + rnd;
}

/** 业务参数 → cryptoSign（md5(拼接串 + 盐)）。服务端当前不校验，保留以兼容。 */
export function cryptoSign(params: Record<string, string | number | undefined>): string {
  const raw = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== "")
    .map(([k, v]) => k + "=" + v)
    .join("&");
  return md5Hex(raw + SIGN_SALT);
}

export class ChelaileClient {
  readonly baseUrl: string;
  readonly userId: string;
  cityId: string;
  geo: GeoPoint;
  private readonly opts: RequestOptions;

  constructor(options: ClientOptions = {}) {
    this.baseUrl = (options.baseUrl ?? DEFAULT_BASE_URL).replace(/\/$/, "");
    this.userId = options.userId ?? randomUserId();
    this.cityId = options.cityId ?? "241";
    this.geo = { lat: options.lat ?? 24.813761, lng: options.lng ?? 113.591965 };
    this.opts = {
      baseUrl: this.baseUrl,
      timeoutMs: options.timeoutMs ?? 12000,
      retries: options.retries ?? 2
    };
  }

  setCity(cityId: string) {
    this.cityId = cityId;
  }

  setGeo(geo: GeoPoint) {
    this.geo = geo;
  }

  /** 城市列表（499 个实时公交城市）。 */
  async listCities(): Promise<City[]> {
    const q = new URLSearchParams({ type: "all", ...CITY_PARAMS });
    const text = await getText(this.baseUrl + "/cdatasource/citylist?" + q.toString(), this.opts);
    const json = JSON.parse(stripWrapper(text)) as { data?: { allRealtimeCity?: City[] } };
    return json.data?.allRealtimeCity ?? [];
  }

  /** 根据坐标反查城市名（接口只返回名字，需再与城市列表匹配取 cityId）。 */
  async locateCity(geo: GeoPoint = this.geo): Promise<string | null> {
    const q = new URLSearchParams({
      type: "gpsRealtimeCity",
      lat: String(geo.lat),
      lng: String(geo.lng),
      gpstype: "wgs",
      ...CITY_PARAMS
    });
    const text = await getText(this.baseUrl + "/cdatasource/citylist?" + q.toString(), this.opts);
    const json = JSON.parse(stripWrapper(text)) as { data?: { gpsRealtimeCity?: { cityName?: string } } };
    return json.data?.gpsRealtimeCity?.cityName ?? null;
  }

  /** 线路搜索：接口会返回该城市全部线路，按名称在本地过滤。 */
  async listCityLines(cityId: string = this.cityId): Promise<LineSummary[]> {
    const data = await this.call<{ allLines?: { all?: LineSummary[] } }>("/api/bus/cityLineList", { cityId });
    return data?.allLines?.all ?? [];
  }

  async searchLines(keyword: string, cityId: string = this.cityId): Promise<LineSummary[]> {
    const all = await this.listCityLines(cityId);
    const kw = keyword.trim().toLowerCase();
    if (!kw) return [];
    const exact = all.filter((l) => l.lineName.toLowerCase() === kw);
    if (exact.length) return exact;
    return all
      .filter((l) => l.lineName.toLowerCase().includes(kw))
      .sort((a, b) => a.lineName.length - b.lineName.length)
      .slice(0, 40);
  }

  /** 线路站序 + 轨迹点。 */
  async getLineRoute(lineId: string, cityId: string = this.cityId): Promise<LineRouteData> {
    return this.call<LineRouteData>("/api/bus/line!lineRoute.action", { lineId, cityId });
  }

  /** 附近线路（首页主链路，加密返回）。 */
  async getNearbyLines(geo: GeoPoint = this.geo, cityId: string = this.cityId): Promise<NearLinesData> {
    return this.call<NearLinesData>("/api/bus/stop!encryptedNearlines.action", {
      cityState: "2",
      cityId,
      lat: geo.lat,
      lng: geo.lng
    });
  }

  /** 附近物理站点（页面：附近站点列表）。 */
  async getNearbyStations(geo: GeoPoint = this.geo, cityId: string = this.cityId): Promise<NearStationsData> {
    return this.call<NearStationsData>("/api/bus/stop!encryptedNearPhysicalStns.action", {
      cityState: "2",
      cityId,
      lat: geo.lat,
      lng: geo.lng
    });
  }

  /** 站点详情：该站所有线路 + 到站预测（加密返回）。 */
  async getStationDetail(physicalStId: string, stnName: string, cityId: string = this.cityId): Promise<StationDetailData> {
    return this.call<StationDetailData>("/api/bus/stop!encryptedPhyStnDetail.action", {
      physicalStId,
      stnName,
      cityId
    });
  }

  /** 线路实时详情（配齐业务参数后服务端才返回数据）。 */
  async getLineDetail(input: {
    lineId: string;
    lineName: string;
    direction: number | string;
    stationName: string;
    nextStationName: string;
    lineNo?: string;
    targetOrder: number | string;
    cityId?: string;
    geo?: GeoPoint;
  }): Promise<LineDetailData> {
    const geo = input.geo ?? this.geo;
    return this.call<LineDetailData>(
      "/api/bus/line!encryptedLineDetail.action",
      {
        lineId: input.lineId,
        lineName: input.lineName,
        direction: input.direction,
        stationName: input.stationName,
        nextStationName: input.nextStationName,
        lineNo: input.lineNo ?? "",
        targetOrder: input.targetOrder,
        cityId: input.cityId ?? this.cityId,
        lat: geo.lat,
        lng: geo.lng
      },
      { cryptoSigned: true }
    );
  }

  /** 车辆列表（非加密链路，字段更少）。 */
  async getBusList(lineId: string, targetOrder: number | string, cityId: string = this.cityId) {
    return this.call<{ buses: Record<string, unknown>[]; line: Record<string, unknown>; targetOrder: number }>(
      "/api/bus/line!busList.action",
      { lineId, targetOrder, cityId }
    );
  }

  private buildQuery(extra: Record<string, string | number | undefined>, geo: GeoPoint): string {
    const params: Record<string, string> = {
      ...H5_PARAMS,
      userId: this.userId,
      h5Id: this.userId,
      cityId: this.cityId,
      geo_lat: String(geo.lat),
      geo_lng: String(geo.lng),
      lat: String(geo.lat),
      lng: String(geo.lng),
      gpstype: "wgs"
    };
    for (const [k, v] of Object.entries(extra)) {
      if (v === undefined || v === null || v === "") continue;
      params[k] = String(v);
    }
    return new URLSearchParams(params).toString();
  }

  private async call<T>(
    path: string,
    extra: Record<string, string | number | undefined>,
    opts: { cryptoSigned?: boolean } = {}
  ): Promise<T> {
    const geo = { lat: this.geo.lat, lng: this.geo.lng };
    const params = { ...extra };
    if (opts.cryptoSigned) {
      const signed: Record<string, string | number | undefined> = {};
      for (const key of ["lineId", "lineName", "direction", "stationName", "nextStationName", "lineNo", "targetOrder"]) {
        signed[key] = params[key];
      }
      params.cryptoSign = cryptoSign(signed);
    }
    const url = this.baseUrl + path + "?" + this.buildQuery(params, geo);
    const text = await getText(url, this.opts);
    const body = stripWrapper(text);
    let json: { jsonr?: { status?: string; errmsg?: string | null; data?: unknown } };
    try {
      json = JSON.parse(body);
    } catch {
      throw new ApiError("parse", path, "响应不是合法 JSON: " + body.slice(0, 120));
    }
    const jsonr = json.jsonr ?? {};
    if (jsonr.status !== "00") {
      throw new ApiError(jsonr.status ?? "unknown", path, jsonr.errmsg ?? "接口返回非 00 状态");
    }
    return this.decode<T>(jsonr.data, path);
  }

  /** data.encryptResult（AES-256-ECB/PKCS7, base64）→ 明文 JSON；否则原样返回。 */
  private decode<T>(data: unknown, path: string): T {
    if (data && typeof data === "object" && "encryptResult" in data) {
      const cipher = String((data as { encryptResult: string }).encryptResult);
      try {
        const plain = aesEcbDecrypt(new TextEncoder().encode(AES_KEY), base64ToBytes(cipher));
        return JSON.parse(bytesToUtf8(plain)) as T;
      } catch (err) {
        throw new ApiError("decrypt", path, "解密失败: " + (err as Error).message);
      }
    }
    return data as T;
  }
}
