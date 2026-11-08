import { useEffect, useState } from "react";
import { api } from "../api.ts";
import { useStore, type Page } from "../store.tsx";
import { LineRow } from "../components/LineRow.tsx";
import { formatDistance, type StationDetailData } from "../../../../packages/protocol/src/index.ts";

type StationPage = Extract<Page, { kind: "station" }>;

export function StationView({ page }: { page: StationPage }) {
  const { city, navigate, showToast } = useStore();
  const [data, setData] = useState<StationDetailData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const cityId = page.cityId || city?.cityId || "241";

  useEffect(() => {
    let alive = true;
    api
      .stationDetail(page.physicalStId, page.name, cityId)
      .then((res) => {
        if (!alive) return;
        setData(res);
        setUpdatedAt(new Date());
        setError(null);
      })
      .catch((err: Error) => alive && setError(err.message));
    return () => {
      alive = false;
    };
  }, [page.physicalStId, page.name, cityId, tick]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") setTick((n) => n + 1);
    }, 15000);
    return () => window.clearInterval(timer);
  }, []);

  const groups = data?.stationList ?? [];
  // 上游会把「同名站」（另一个物理站台）也塞进 stationList，但那部分只给了线路号，
  // 没有起终站与实时数据。拆开渲染，避免出现一排「→ 暂无车辆信息」的空行。
  const primary = groups.filter((g) => g.lines.some((l) => l.line.lineId));
  const namesakes = groups.filter((g) => !g.lines.some((l) => l.line.lineId));
  const lineCount = primary.reduce((n, g) => n + g.lines.length, 0);
  const namesakeCount = namesakes.reduce((n, g) => n + g.lines.length, 0);

  return (
    <div>
      <div className="card" style={{ padding: 16 }}>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>公交站</div>
        <div style={{ fontSize: 20, fontWeight: 700, marginTop: 2 }}>{page.name}</div>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 2 }}>
          {lineCount > 0 ? lineCount + " 条线路" : "暂无线路"}
          {namesakeCount > 0 ? " · 同名站另有 " + namesakeCount + " 条" : ""}
          {updatedAt ? " · 更新于 " + updatedAt.toLocaleTimeString("zh-CN", { hour12: false }) : ""}
        </div>
      </div>

      {error ? <div className="card"><div className="empty">⚠️ {error}</div></div> : null}
      {!data && !error ? <div className="skeleton" style={{ marginTop: 12 }} /> : null}

      {primary.map((group, gi) => (
        <div className="card stop-group" key={String(group.physicalStId ?? gi)}>
          <div className="stop-head">
            <span className="name">{String(group.sn ?? page.name)}</span>
            {formatDistance(group.distance) ? <span className="dist">{formatDistance(group.distance)}</span> : null}
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
                  cityId
                })
              }
            />
          ))}
        </div>
      ))}

      {namesakes.map((group, gi) => (
        <div className="card stop-group" key={"ns-" + String(group.physicalStId ?? gi)}>
          <div className="stop-head">
            <span className="name">同名站</span>
            {typeof group.physicalStId === "string" ? (
              <button
                className="link-btn"
                onClick={() => navigate({ kind: "station", physicalStId: String(group.physicalStId), name: page.name, cityId })}
              >
                查看该站 ›
              </button>
            ) : null}
          </div>
          <div className="chip-row" style={{ padding: "12px 16px 14px", margin: 0 }}>
            {group.lines.map((near, li) => (
              <span className="chip chip-static" key={near.line.name + "-" + li}>
                {near.line.name} 路
              </span>
            ))}
          </div>
        </div>
      ))}

      <div className="footer-note">
        <button style={{ color: "var(--accent)", fontSize: 12 }} onClick={() => setTick((n) => n + 1)}>
          刷新到站信息
        </button>
        <span style={{ margin: "0 8px" }}>·</span>
        <button
          style={{ color: "var(--accent)", fontSize: 12 }}
          onClick={() => {
            navigator.clipboard?.writeText(page.name).then(
              () => showToast("站名已复制"),
              () => showToast("复制失败")
            );
          }}
        >
          复制站名
        </button>
      </div>
    </div>
  );
}