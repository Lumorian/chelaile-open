import { useEffect, useMemo, useState } from "react";
import { StarIcon, BusIcon } from "../components/Icons.tsx";
import { api } from "../api.ts";
import { useStore, type Page } from "../store.tsx";
import { nearestIndex } from "../lib/geo.ts";
import { formatClock, formatDistance, lineTitle, operationWindow, type LineDetailData, type LineRouteData } from "../../../../packages/protocol/src/index.ts";

type LinePage = Extract<Page, { kind: "line" }>;

export function LineView({ page }: { page: LinePage }) {
  const { geo, isFavorite, toggleFavorite, showToast, city } = useStore();
  const [route, setRoute] = useState<LineRouteData | null>(null);
  const [detail, setDetail] = useState<LineDetailData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [tick, setTick] = useState(0);

  const cityId = page.cityId || city?.cityId || "241";

  useEffect(() => {
    let alive = true;
    api
      .lineRoute(page.lineId, cityId)
      .then((data) => alive && setRoute(data))
      .catch((err: Error) => alive && setError(err.message));
    return () => {
      alive = false;
    };
  }, [page.lineId, cityId]);

  const targetIndex = useMemo(() => (route ? nearestIndex(route.stations, geo) : 0), [route, geo]);

  useEffect(() => {
    if (!route || route.stations.length === 0) return;
    const target = route.stations[Math.min(targetIndex, route.stations.length - 1)];
    const next = route.stations[Math.min(targetIndex + 1, route.stations.length - 1)];
    let alive = true;
    api
      .lineDetail({
        lineId: page.lineId,
        lineName: page.lineName || String(route.line.name ?? ""),
        direction: 0,
        stationName: target.sn,
        nextStationName: next.sn,
        targetOrder: target.order,
        cityId
      })
      .then((data) => {
        if (!alive) return;
        setDetail(data);
        setUpdatedAt(new Date());
        setError(null);
      })
      .catch((err: Error) => alive && setError(err.message));
    return () => {
      alive = false;
    };
  }, [route, targetIndex, page.lineId, page.lineName, cityId, tick]);

  // 15 秒轮询（页面可见时才刷）
  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") setTick((n) => n + 1);
    }, 15000);
    return () => window.clearInterval(timer);
  }, [autoRefresh]);

  const stations = route?.stations ?? detail?.stations ?? [];
  const info = detail?.line;
  const favorite = isFavorite(page.lineId);
  const busOrders = new Set((detail?.buses ?? []).map((b) => Number(b.order)).filter((n) => Number.isFinite(n)));
  const status = info ? String(info.shortDesc || info.desc || "") : "";
  const assist = info?.assistDesc ? String(info.assistDesc) : "";
  const nextOp = info?.nextOperationTimeDesc ? String(info.nextOperationTimeDesc) : "";

  return (
    <div>
      <div className="card line-hero">
        <div className="h">
          <span className="badge">{page.lineName || info?.name || "?"}</span>
          <div style={{ flex: 1 }}>
            <div className="dir">{info ? lineTitle(info) : page.startSn + " → " + page.endSn}</div>
            <div className="meta">
              {info?.price ? info.price + " · " : ""}
              {info ? operationWindow(info) : ""}
              {info?.stationsNum ? " · 共 " + info.stationsNum + " 站" : ""}
            </div>
          </div>
          <button className="icon-btn" title="收藏" onClick={() => {
            toggleFavorite({
              lineId: page.lineId,
              lineName: page.lineName || String(info?.name ?? ""),
              startSn: page.startSn || String(info?.startSn ?? ""),
              endSn: page.endSn || String(info?.endSn ?? ""),
              cityId
            });
            showToast(favorite ? "已取消关注" : "已加入关注");
          }}>
            <StarIcon size={20} filled={favorite} />
          </button>
        </div>

        <div className="pill-row">
          <span className={"pill " + (detail?.realData ? "live" : "")}>{detail?.realData ? "实时数据" : "预测/静态"}</span>
          {status ? <span className="pill">{status}</span> : null}
          {assist ? <span className="pill live">{assist}</span> : null}
          {nextOp ? <span className="pill">{nextOp}</span> : null}
        </div>

        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 14, fontSize: 12, color: "var(--text-tertiary)" }}>
          <span>
            最近站：{stations[targetIndex]?.sn ?? "—"}
            {stations[targetIndex]?.distanceToSp ? " · " + formatDistance(stations[targetIndex].distanceToSp) : ""}
          </span>
          <span>
            {updatedAt ? "更新于 " + formatClock(updatedAt) : "加载中…"}
            {autoRefresh ? " · 自动刷新" : ""}
          </span>
        </div>
      </div>

      {error ? (
        <div className="card">
          <div className="empty">⚠️ {error}</div>
        </div>
      ) : null}

      <div className="section-title">
        <span>途经站点</span>
        <span>{stations.length} 站</span>
      </div>

      <div className="card timeline">
        {stations.map((station, index) => {
          const isTarget = index === targetIndex;
          const hasBus = busOrders.has(Number(station.order));
          const passed = busOrders.size > 0 && Number(station.order) < Math.min(...busOrders);
          return (
            <div key={station.sId + "-" + index} className={"tl-item" + (isTarget ? " target" : "") + (passed ? " passed" : "")}>
              <span className="rail" />
              <span className="dot" style={hasBus ? { background: "var(--success)", borderColor: "var(--success)" } : undefined} />
              <span>
                <span className="sn">{station.sn}</span>
                {station.metros && station.metros.length > 0 ? (
                  <div className="metro">地铁 {station.metros.map((m) => m.fullName ?? m.lineNo).join(" / ")}</div>
                ) : null}
              </span>
              <span className="ord">{hasBus ? <BusIcon size={13} /> : station.distanceToSp ? formatDistance(station.distanceToSp) : ""}</span>
            </div>
          );
        })}
        {stations.length === 0 ? <div className="empty">暂无站点数据</div> : null}
      </div>

      <div className="footer-note">
        到站时间仅供参考，实际以车辆运行为准。
        <br />
        <button style={{ color: "var(--accent)", fontSize: 12 }} onClick={() => setTick((n) => n + 1)}>
          立即刷新
        </button>
        <span style={{ margin: "0 8px" }}>·</span>
        <button style={{ color: "var(--accent)", fontSize: 12 }} onClick={() => setAutoRefresh((v) => !v)}>
          {autoRefresh ? "关闭自动刷新" : "开启自动刷新"}
        </button>
      </div>
    </div>
  );
}