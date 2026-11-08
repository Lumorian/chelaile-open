import { useEffect, useState } from "react";
import { api } from "../api.ts";
import { useStore } from "../store.tsx";
import { LineRow } from "../components/LineRow.tsx";
import { formatDistance, type NearLinesData, type NearStationsData } from "../../../../packages/protocol/src/index.ts";

export function NearbyView() {
  const { city, geo, navigate, refreshTick, locating, locate, showToast } = useStore();
  const [lines, setLines] = useState<NearLinesData | null>(null);
  const [stations, setStations] = useState<NearStationsData | null>(null);
  const [mode, setMode] = useState<"lines" | "stops">("lines");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!city) return;
    let alive = true;
    setLoading(true);
    Promise.all([api.nearbyLines(city.cityId, geo.lat, geo.lng), api.nearbyStations(city.cityId, geo.lat, geo.lng)])
      .then(([nearLines, nearStations]) => {
        if (!alive) return;
        setLines(nearLines);
        setStations(nearStations);
        setError(null);
      })
      .catch((err: Error) => alive && setError(err.message))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [city, geo.lat, geo.lng, refreshTick, tick]);

  // 每 20 秒自动刷新一次到站信息（页面可见时）
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") setTick((n) => n + 1);
    }, 20000);
    return () => window.clearInterval(timer);
  }, []);

  const groups = (lines?.nearLines ?? []).filter((group) => group.lines.length > 0);
  const stops = stations?.nearStations ?? [];

  return (
    <div>
      <div className="card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>当前位置 · {city?.cityName ?? "未选择城市"}</div>
          <div style={{ fontSize: 17, fontWeight: 600, marginTop: 2 }}>
            {stops[0]?.sn ?? (groups[0]?.lines?.[0]?.targetStation?.sn ?? "正在定位附近站点")}
          </div>
          <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 2 }}>
            {geo.lat.toFixed(5)}, {geo.lng.toFixed(5)}
          </div>
        </div>
        <button
          className="icon-btn"
          title="重新定位"
          onClick={async () => {
            const name = await locate();
            if (name) showToast("已定位到 " + name);
          }}
          style={{ width: 40, height: 40, fontSize: 18 }}
        >
          {locating ? "…" : "◎"}
        </button>
      </div>

      <div className="chip-row">
        <button className={"chip " + (mode === "lines" ? "active" : "")} onClick={() => setMode("lines")}>
          附近线路 {mode === "lines" ? "·" + groups.reduce((n, g) => n + g.lines.length, 0) : ""}
        </button>
        <button className={"chip " + (mode === "stops" ? "active" : "")} onClick={() => setMode("stops")}>
          附近站点 {mode === "stops" ? "·" + stops.length : ""}
        </button>
      </div>

      {error ? (
        <div className="card">
          <div className="empty">
            <div className="big">⚠️</div>
            {error}
            <div style={{ marginTop: 12, fontSize: 12 }}>请确认本地 BFF（npm run dev:server）已启动。</div>
          </div>
        </div>
      ) : null}

      {loading && !lines ? (
        <>
          <div className="skeleton" />
          <div className="skeleton" />
          <div className="skeleton" />
        </>
      ) : null}

      {mode === "lines"
        ? groups.map((group, gi) => (
            <div className="card stop-group" key={gi}>
              <div className="stop-head">
                <span className="name">{group.lines[0]?.targetStation?.sn ?? "附近站点"}</span>
                <span className="dist">{formatDistance(group.distance)}</span>
              </div>
              {group.lines.map((near, li) => (
                <LineRow
                  key={near.line.lineId + "-" + li}
                  near={near}
                  showStop={false}
                  onClick={() =>
                    navigate({
                      kind: "line",
                      lineId: near.line.lineId,
                      lineName: near.line.name,
                      startSn: near.line.startSn,
                      endSn: near.line.endSn,
                      cityId: city?.cityId ?? ""
                    })
                  }
                />
              ))}
            </div>
          ))
        : stops.map((stop) => (
            <div className="card" key={stop.sId}>
              <button
                className="line-row"
                onClick={() =>
                  navigate({
                    kind: "station",
                    physicalStId: stop.physicalStId ?? stop.sId,
                    name: stop.sn,
                    cityId: city?.cityId ?? ""
                  })
                }
              >
                <span className="line-badge" style={{ background: "var(--bg-sunken)", color: "var(--text-secondary)" }}>站</span>
                <span className="line-body">
                  <span className="line-name">{stop.sn}</span>
                  <span className="line-sub">{stop.sType === 1 ? "公交站" : "站点"}</span>
                </span>
                <span className="arrival">
                  <span className="primary idle">{formatDistance(stop.distance)}</span>
                </span>
              </button>
            </div>
          ))}

      {!loading && !error && groups.length === 0 && mode === "lines" ? (
        <div className="card">
          <div className="empty">
            <div className="big">🚏</div>
            附近 1 公里内没有找到线路
            <div style={{ marginTop: 8, fontSize: 12 }}>换个城市或调整坐标试试（设置 → 坐标）。</div>
          </div>
        </div>
      ) : null}

      <div className="footer-note">
        数据来源：车来了公开 H5 接口 · 本地缓存 15 秒
        <br />
        本应用与「车来了」官方无关，仅供学习研究
      </div>
    </div>
  );
}