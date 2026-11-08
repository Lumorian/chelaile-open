import { useState } from "react";
import { useStore } from "../store.tsx";
import { CitySheet } from "../components/CitySheet.tsx";

export function SettingsView() {
  const { city, geo, setGeo, demoMode, setDemoMode, locate, showToast, favorites } = useStore();
  const [sheet, setSheet] = useState(false);
  const [lat, setLat] = useState(String(geo.lat));
  const [lng, setLng] = useState(String(geo.lng));

  return (
    <div>
      <div className="section-title">通用</div>
      <div className="card">
        <button className="line-row" onClick={() => setSheet(true)}>
          <span className="line-body">
            <span className="line-name">城市</span>
            <span className="line-sub">决定查询哪座城市的公交数据</span>
          </span>
          <span className="arrival">
            <span className="secondary">{city?.cityName ?? "未选择"} ›</span>
          </span>
        </button>
        <button
          className="line-row"
          onClick={async () => {
            const name = await locate();
            if (name) {
              showToast("已定位到 " + name);
              setLat(String(geo.lat));
              setLng(String(geo.lng));
            }
          }}
        >
          <span className="line-body">
            <span className="line-name">使用浏览器定位</span>
            <span className="line-sub">需要授予定位权限</span>
          </span>
          <span className="arrival">
            <span className="secondary">定位 ›</span>
          </span>
        </button>
      </div>

      <div className="section-title">坐标（手工覆盖）</div>
      <div className="card">
        <div className="field">
          <label>纬度 latitude</label>
          <input value={lat} onChange={(e) => setLat(e.target.value)} inputMode="decimal" />
        </div>
        <div className="field">
          <label>经度 longitude</label>
          <input value={lng} onChange={(e) => setLng(e.target.value)} inputMode="decimal" />
        </div>
        <div style={{ padding: "0 16px 16px" }}>
          <button
            className="chip active"
            style={{ width: "100%", padding: "10px 16px", fontSize: 14 }}
            onClick={() => {
              const nextLat = Number(lat);
              const nextLng = Number(lng);
              if (!Number.isFinite(nextLat) || !Number.isFinite(nextLng)) {
                showToast("坐标格式不正确");
                return;
              }
              setGeo({ lat: nextLat, lng: nextLng });
              showToast("已保存坐标");
            }}
          >
            保存坐标
          </button>
        </div>
      </div>

      <div className="section-title">实验</div>
      <div className="card">
        <div className="switch-row">
          <div>
            <div className="t">演示数据模式</div>
            <div className="d">无网络时用内置样例展示界面（开发用）</div>
          </div>
          <button className={"switch " + (demoMode ? "on" : "")} onClick={() => setDemoMode(!demoMode)} aria-label="演示模式" />
        </div>
      </div>

      <div className="section-title">关于</div>
      <div className="card">
        <div className="field">
          <label>项目</label>
          <div>车来了 · 开放版（chelaile-open）</div>
        </div>
        <div className="field">
          <label>说明</label>
          <div style={{ fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.7 }}>
            本项目通过逆向分析「车来了」Android 客户端的公开 H5 接口实现，仅用于学习与技术研究。
            所有数据版权归原公司所有，请勿用于商业用途。
          </div>
        </div>
        <div className="field">
          <label>本地缓存</label>
          <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>
            关注线路 {favorites.length} 条 · 坐标与城市保存在浏览器本地
          </div>
        </div>
      </div>

      <CitySheet open={sheet} onClose={() => setSheet(false)} />
      <div className="footer-note">MIT License · 由社区维护</div>
    </div>
  );
}