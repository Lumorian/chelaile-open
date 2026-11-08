import { useStore } from "../store.tsx";

export function FavoritesView() {
  const { favorites, navigate, toggleFavorite, showToast, city } = useStore();

  return (
    <div>
      <div className="section-title">
        <span>我的关注</span>
        <span>{favorites.length} 条线路</span>
      </div>

      {favorites.length === 0 ? (
        <div className="card">
          <div className="empty">
            <div className="big">☆</div>
            还没有关注的线路
            <div style={{ marginTop: 8, fontSize: 12 }}>在线路详情页点右上角星标即可加入关注。</div>
          </div>
        </div>
      ) : (
        <div className="card">
          {favorites.map((f) => (
            <div key={f.lineId} className="line-row" style={{ cursor: "default" }}>
              <button
                className="line-badge"
                onClick={() => navigate({ kind: "line", lineId: f.lineId, lineName: f.lineName, startSn: f.startSn, endSn: f.endSn, cityId: f.cityId })}
              >
                {f.lineName}
              </button>
              <button
                className="line-body"
                style={{ textAlign: "left" }}
                onClick={() => navigate({ kind: "line", lineId: f.lineId, lineName: f.lineName, startSn: f.startSn, endSn: f.endSn, cityId: f.cityId })}
              >
                <span className="line-name">
                  {f.startSn} → {f.endSn}
                </span>
                <span className="line-sub">
                  {f.cityId === city?.cityId ? "当前城市" : "城市 " + f.cityId} · 关注于{" "}
                  {new Date(f.addedAt).toLocaleDateString("zh-CN")}
                </span>
              </button>
              <button
                className="arrival"
                onClick={() => {
                  toggleFavorite(f);
                  showToast("已取消关注 " + f.lineName);
                }}
              >
                <span className="secondary">移除</span>
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="footer-note">
        关注列表保存在浏览器本地（localStorage），不会上传到任何服务器。
      </div>
    </div>
  );
}