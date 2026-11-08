import { stationToWgs } from "../../../../packages/protocol/src/index.ts";

export function haversine(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/**
 * 在站序里找到离用户最近的一站，返回其下标。
 *
 * 注意坐标系：lineRoute 返回的是 BD-09，用户坐标是 WGS-84，
 * 直接比较会偏几百米，所以先用 stationToWgs 统一（有 wgsLat 时优先）。
 */
export function nearestIndex<T extends { lat: number; lng: number; wgsLat?: number; wgsLng?: number; gpsType?: string }>(
  stations: T[],
  geo: { lat: number; lng: number }
): number {
  let best = 0;
  let bestDist = Infinity;
  stations.forEach((s, i) => {
    if (!s.lat || !s.lng) return;
    const d = haversine(geo, stationToWgs(s));
    if (d < bestDist) {
      bestDist = d;
      best = i;
    }
  });
  return best;
}
