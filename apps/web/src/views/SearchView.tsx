import { useEffect, useRef, useState } from "react";
import { api } from "../api.ts";
import { SearchIcon } from "../components/Icons.tsx";
import { useStore } from "../store.tsx";
import type { LineSummary } from "../../../../packages/protocol/src/index.ts";

export function SearchView() {
  const { city, navigate, favorites } = useStore();
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<LineSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!city) return;
    const kw = keyword.trim();
    if (!kw) {
      setResults([]);
      return;
    }
    let alive = true;
    setLoading(true);
    const timer = window.setTimeout(() => {
      api
        .searchLines(city.cityId, kw, 40)
        .then((rows) => alive && (setResults(rows), setError(null)))
        .catch((err: Error) => alive && setError(err.message))
        .finally(() => alive && setLoading(false));
    }, 220);
    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [keyword, city]);

  const hotKeywords = ["1 路", "2 路", "5 路", "10 路", "38 路", "快线", "夜班", "机场"];

  return (
    <div>
      <div className="searchbar">
        <span style={{ color: "var(--text-tertiary)", display: "inline-flex" }}><SearchIcon size={18} /></span>
        <input
          ref={inputRef}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder={"在 " + (city?.cityName ?? "全部城市") + " 搜索线路"}
          enterKeyHint="search"
        />
        {keyword ? (
          <button onClick={() => setKeyword("")} style={{ color: "var(--text-tertiary)" }}>
            ✕
          </button>
        ) : null}
      </div>

      {!keyword ? (
        <>
          <div className="section-title">热门搜索</div>
          <div className="chip-row">
            {hotKeywords.map((k) => (
              <button key={k} className="chip" onClick={() => setKeyword(k)}>
                {k}
              </button>
            ))}
          </div>
          {favorites.length > 0 ? (
            <>
              <div className="section-title">我的关注</div>
              <div className="card">
                {favorites.slice(0, 5).map((f) => (
                  <button
                    key={f.lineId}
                    className="line-row"
                    onClick={() =>
                      navigate({ kind: "line", lineId: f.lineId, lineName: f.lineName, startSn: f.startSn, endSn: f.endSn, cityId: f.cityId })
                    }
                  >
                    <span className="line-badge">{f.lineName}</span>
                    <span className="line-body">
                      <span className="line-name">
                        {f.startSn} → {f.endSn}
                      </span>
                    </span>
                    <span className="chevron" aria-hidden="true">›</span>
                  </button>
                ))}
              </div>
            </>
          ) : null}
        </>
      ) : null}

      {loading ? <div className="skeleton" style={{ marginTop: 12 }} /> : null}
      {error ? <div className="empty">{error}</div> : null}

      {results.length > 0 ? (
        <div className="card" style={{ marginTop: 12 }}>
          {results.map((line) => (
            <button
              key={line.lineId}
              className="line-row"
              onClick={() =>
                navigate({
                  kind: "line",
                  lineId: line.lineId,
                  lineName: line.lineName,
                  startSn: line.startStopName,
                  endSn: line.endStopName,
                  cityId: city?.cityId ?? ""
                })
              }
            >
              <span className="line-badge">{line.lineName}</span>
              <span className="line-body">
                <span className="line-name">
                  {line.startStopName} → {line.endStopName}
                </span>
                <span className="line-sub">
                  {line.firstTime ?? ""}
                  {line.firstTime ? "–" : ""}
                  {line.lastTime ?? ""}
                </span>
              </span>
              <span className="chevron" aria-hidden="true">›</span>
            </button>
          ))}
        </div>
      ) : null}

      {!loading && keyword && results.length === 0 && !error ? (
        <div className="empty">
          <div className="big"><SearchIcon size={40} /></div>
          没有找到「{keyword}」相关线路
        </div>
      ) : null}
    </div>
  );
}