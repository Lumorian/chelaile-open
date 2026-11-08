import type { BusInfo, LineInfo, NearLine, StationRef, StnState } from "./types.ts";

export interface ArrivalView {
  /** 主文案：如 "3分钟" / "即将到站" / "首班未发" */
  primary: string;
  /** 次文案：如 "还有2站" / "首班预计 06:50 发车" */
  secondary: string;
  /** 是否处于实时到站状态 */
  live: boolean;
  /** 供排序用：预计到站秒数，未知为 Infinity */
  etaSeconds: number;
}

export function formatDistance(meters: number | undefined | null): string {
  if (meters === undefined || meters === null || Number.isNaN(meters) || meters < 0) return "";
  if (meters < 1000) return Math.round(meters) + "米";
  return (meters / 1000).toFixed(meters < 10000 ? 1 : 0) + "公里";
}

export function formatClock(date = new Date()): string {
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}

/**
 * 从 stnStates 里抽取车辆信息。
 *
 * 线上 stnStates 的形态在不同城市/版本间并不统一：有的直接给 `{order,time}`，
 * 有的给 `{order, buses:[...]}`。这里两边都吃，拿不到就返回空数组。
 */
function collectBuses(line: NearLine): BusInfo[] {
  const out: BusInfo[] = [];
  const states = (line.stnStates ?? []) as StnState[];
  for (const st of states) {
    if (Array.isArray(st.buses)) out.push(...st.buses);
    else if (typeof st.time === "number" || typeof st.order === "number") {
      out.push({ order: st.order, time: st.time, distance: st.distance, sId: st.sId } as BusInfo);
    }
  }
  return out.sort((a, b) => (a.time ?? 1e9) - (b.time ?? 1e9));
}

function minutesText(seconds: number): string {
  const minutes = Math.max(0, Math.round(seconds / 60));
  if (minutes <= 0) return "即将到站";
  if (minutes === 1) return "1分钟";
  return minutes + "分钟";
}

/**
 * 把一条线路的原始数据压成一句“到站文案”。
 *
 * 优先级：实时车辆（stnStates）→ 发车间隔 → 运营状态文案（首班未发 / 已收班 / 暂无车辆信息）。
 * 所有兜底都基于线上真实字段，见 docs/02-协议参考.md。
 */
export function summarize(line: NearLine): ArrivalView {
  const info: LineInfo = line.line ?? ({ lineId: "", name: "", startSn: "", endSn: "" } as LineInfo);
  const target = line.targetStation as StationRef | undefined;
  const buses = collectBuses(line);
  const first = buses[0];
  if (first) {
    const seconds = typeof first.time === "number" ? first.time : undefined;
    const stops = typeof first.order === "number" && target?.order !== undefined ? target.order - first.order : undefined;
    const primary = seconds !== undefined ? minutesText(seconds) : stops !== undefined && stops <= 0 ? "即将到站" : stops !== undefined ? stops + "站" : "即将到站";
    const secondary = stops !== undefined && stops > 0 ? "还有" + stops + "站" : "";
    return {
      primary,
      secondary,
      live: true,
      etaSeconds: seconds ?? (stops !== undefined ? stops * 120 : Infinity)
    };
  }

  const interval = Number(line.depIntervalM ?? info.depIntervalM ?? -1);
  const status = String(info.shortDesc || info.desc || info.assistDesc || "");
  if (interval > 0) {
    return { primary: "约" + interval + "分钟一班", secondary: status, live: false, etaSeconds: Infinity };
  }
  const nextDesc = info.nextOperationTimeDesc ? String(info.nextOperationTimeDesc) : "";
  let secondary = nextDesc;
  if (!secondary && info.firstTime && info.lastTime) secondary = "首班 " + info.firstTime + " · 末班 " + info.lastTime;
  return { primary: status || "暂无车辆信息", secondary, live: false, etaSeconds: Infinity };
}

export function lineTitle(line: LineInfo): string {
  const start = line.startSn ?? "";
  const end = line.endSn ?? line.destinationName ?? "";
  if (!start && !end) return "";
  return start + " → " + end;
}

/** 距离用户最近的站（用于首页卡片副标题）。 */
export function nearestStopLabel(line: NearLine): string {
  const t = line.targetStation;
  if (!t) return "";
  const d = formatDistance(t.distanceToSp);
  return d ? t.sn + " · " + d : t.sn;
}

/** 运营时间摘要，如 "06:50–17:20"。 */
export function operationWindow(line: LineInfo): string {
  if (!line.firstTime && !line.lastTime) return "";
  const parts = (line.segTimes ?? []) as Array<[string, string]>;
  if (parts.length > 1) return parts.map(([a, b]) => a + "–" + b).join(" / ");
  return (line.firstTime ?? "") + "–" + (line.lastTime ?? "");
}
