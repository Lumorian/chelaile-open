import { summarize, formatDistance, type NearLine } from "../../../../packages/protocol/src/index.ts";

/**
 * 一条线路的紧凑列表行。
 *
 * 布局是「徽标 | 内容区」，内容区第一行是完整的起终站（独占整行宽度，
 * 这样才能放得下「碧桂园（凤凰城） → 碧桂园（太阳城）」这类长站名），
 * 第二行左边是价格与运营时间，右边是到站文案。早期版本把到站文案放在
 * 第一行右侧，结果第二列只剩 190px，所有站名都被截断。
 */
export function LineRow({ near, onClick, showStop = true }: { near: NearLine; onClick?: () => void; showStop?: boolean }) {
  const view = summarize(near);
  const info = near.line;
  const stop = near.targetStation;
  const title = info.startSn || info.endSn ? info.startSn + " → " + (info.endSn ?? "") : (info.destinationName ?? "");
  const meta = [
    showStop && stop ? stop.sn + (stop.distanceToSp ? " · " + formatDistance(stop.distanceToSp) : "") : "",
    info.firstTime ? info.firstTime + "–" + (info.lastTime ?? "") : ""
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <button className="line-row" onClick={onClick}>
      <span className="line-badge">{info.name}</span>
      <span className="line-body">
        <span className="line-name">{title}</span>
        <span className="line-foot">
          <span className="line-sub">
            {info.price ? <span className="price">{info.price}</span> : null}
            {meta || (info.price ? "" : "—")}
          </span>
          <span className="arrival">
            <span className={"primary " + (view.live ? "live" : "idle")}>{view.primary}</span>
            {view.secondary ? <span className="secondary">{view.secondary}</span> : null}
          </span>
        </span>
      </span>
    </button>
  );
}
