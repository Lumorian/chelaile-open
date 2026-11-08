import { useMemo, useState } from "react";
import type { City } from "../../../../packages/protocol/src/index.ts";
import { useStore } from "../store.tsx";
import { SearchIcon } from "./Icons.tsx";

export function CitySheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { cities, city, setCity } = useStore();
  const [keyword, setKeyword] = useState("");
  const list = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    const rows = kw
      ? cities.filter((c) => c.cityName.includes(kw) || c.pinyin.toLowerCase().includes(kw))
      : cities.slice(0, 60);
    return rows;
  }, [cities, keyword]);

  if (!open) return null;
  return (
    <>
      <div className="sheet-mask" onClick={onClose} />
      <div className="sheet">
        <div className="sheet-head">
          <strong>选择城市</strong>
          <button onClick={onClose} style={{ color: "var(--text-secondary)" }}>关闭</button>
        </div>
        <div style={{ padding: "12px 16px" }}>
          <div className="searchbar">
            <span style={{ color: "var(--text-tertiary)", display: "inline-flex" }}><SearchIcon size={18} /></span>
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="城市名 / 拼音" autoFocus />
          </div>
        </div>
        <div className="sheet-body">
          {list.map((c) => (
            <button
              key={c.cityId}
              className="city-item"
              onClick={() => {
                setCity(c);
                onClose();
              }}
            >
              <span className={c.hot ? "hot" : ""}>{c.cityName}</span>
              <span style={{ color: "var(--text-tertiary)", fontSize: 12 }}>
                {c.cityId === city?.cityId ? "当前" : c.supportSubway ? "地铁" : ""}
              </span>
            </button>
          ))}
          {list.length === 0 ? <div className="empty">没有找到该城市</div> : null}
        </div>
      </div>
    </>
  );
}