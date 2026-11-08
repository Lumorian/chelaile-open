/**
 * 坐标系转换：BD-09（百度） / GCJ-02（火星） / WGS-84（GPS）。
 *
 * 为什么需要：车来了的 `line!lineRoute.action` 返回的站点坐标是 **BD-09**（`gpstype: "bd"`），
 * 而浏览器定位拿到的是 **WGS-84**。直接拿两者算距离会偏 500 米以上（在北京约 1.2 公里），
 * 于是"最近站"经常选错。`line!encryptedLineDetail` 会额外给出 `wgsLat/wgsLng`，优先用它。
 */

const PI = Math.PI;
const X_PI = (PI * 3000.0) / 180.0;
const A = 6378245.0;
const EE = 0.00669342162296594323;

/** 中国大陆粗略边界：境外不做偏移（与高德/百度的实现保持一致）。 */
export function outOfChina(lat: number, lng: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
}

function transformLat(x: number, y: number): number {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0;
  return ret;
}

/** WGS-84 → GCJ-02。 */
export function wgs84ToGcj02(lat: number, lng: number): { lat: number; lng: number } {
  if (outOfChina(lat, lng)) return { lat, lng };
  let dLat = transformLat(lng - 105.0, lat - 35.0);
  let dLng = transformLng(lng - 105.0, lat - 35.0);
  const radLat = (lat / 180.0) * PI;
  let magic = Math.sin(radLat);
  magic = 1 - EE * magic * magic;
  const sqrtMagic = Math.sqrt(magic);
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI);
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI);
  return { lat: lat + dLat, lng: lng + dLng };
}

/** GCJ-02 → WGS-84（迭代反解，误差 < 1 米）。 */
export function gcj02ToWgs84(lat: number, lng: number): { lat: number; lng: number } {
  if (outOfChina(lat, lng)) return { lat, lng };
  let guessLat = lat;
  let guessLng = lng;
  for (let i = 0; i < 4; i++) {
    const forward = wgs84ToGcj02(guessLat, guessLng);
    guessLat -= forward.lat - lat;
    guessLng -= forward.lng - lng;
  }
  return { lat: guessLat, lng: guessLng };
}

/** BD-09 → GCJ-02。 */
export function bd09ToGcj02(lat: number, lng: number): { lat: number; lng: number } {
  const x = lng - 0.0065;
  const y = lat - 0.006;
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
  return { lat: z * Math.sin(theta), lng: z * Math.cos(theta) };
}

/** GCJ-02 → BD-09。 */
export function gcj02ToBd09(lat: number, lng: number): { lat: number; lng: number } {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
  return { lat: z * Math.sin(theta) + 0.006, lng: z * Math.cos(theta) + 0.0065 };
}

/** BD-09 → WGS-84。 */
export function bd09ToWgs84(lat: number, lng: number): { lat: number; lng: number } {
  const gcj = bd09ToGcj02(lat, lng);
  return gcj02ToWgs84(gcj.lat, gcj.lng);
}

/** 把接口返回的站点坐标统一转成 WGS-84，用于与浏览器定位比较。 */
export function stationToWgs(station: {
  lat: number;
  lng: number;
  wgsLat?: number;
  wgsLng?: number;
  gpsType?: string;
}): { lat: number; lng: number } {
  if (typeof station.wgsLat === "number" && typeof station.wgsLng === "number" && station.wgsLat > 0 && station.wgsLng > 0) {
    return { lat: station.wgsLat, lng: station.wgsLng };
  }
  if (station.gpsType === "bd") return bd09ToWgs84(station.lat, station.lng);
  return { lat: station.lat, lng: station.lng };
}
