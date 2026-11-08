package org.chelaileopen.app.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Geo;
import org.chelaileopen.app.core.Models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 搜索：线路号/线路名，或地名（POI/公交站）并给出当前位置乘车推荐。 */
public class SearchScreen extends Screen {
    private EditText input;
    private LinearLayout results;
    private TextView hint;
    private TextView lineTab;
    private TextView placeTab;
    private boolean searching;
    private boolean recommending;
    private boolean placeMode;
    private Models.PlaceSearch lastPlaces;

    public SearchScreen(MainActivity host) {
        super(host);
    }

    @Override
    public String title() {
        return "搜索";
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 28));

        LinearLayout modes = Ui.row(host);
        lineTab = modeButton("线路", false);
        placeTab = modeButton("地点", true);
        LinearLayout.LayoutParams ml1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ml1.rightMargin = Ui.dp(host, 8);
        modes.addView(lineTab, ml1);
        modes.addView(placeTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lineTab.setOnClickListener(v -> setMode(false));
        placeTab.setOnClickListener(v -> setMode(true));
        root.addView(modes);

        LinearLayout bar = Ui.row(host);
        bar.setPadding(0, Ui.dp(host, 10), 0, 0);
        input = new EditText(host);
        input.setHint("线路号，如 38、101（马坝⇔梅村）");
        input.setHintTextColor(Ui.FAINT);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(Ui.sp(host, 15));
        input.setSingleLine(true);
        input.setBackground(Ui.round(Ui.SURFACE, Ui.dp(host, 10)));
        input.setPadding(Ui.dp(host, 12), Ui.dp(host, 8), Ui.dp(host, 12), Ui.dp(host, 8));
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((v, actionId, event) -> {
            doSearch(input.getText().toString());
            return true;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView btn = Ui.button(host, "搜索", Ui.ACCENT, 0xFF0B1220, v -> doSearch(input.getText().toString()));
        LinearLayout.LayoutParams blp = Ui.wrapWrap();
        blp.leftMargin = Ui.dp(host, 8);
        bar.addView(btn, blp);
        root.addView(bar);

        hint = Ui.text(host, "在 " + app.cityName() + " 搜索线路；切换到「地点」可按地名找公交站", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams hlp = Ui.matchWrap();
        hlp.topMargin = Ui.dp(host, 10);
        root.addView(hint, hlp);

        results = Ui.column(host);
        LinearLayout.LayoutParams rlp = Ui.matchWrap();
        rlp.topMargin = Ui.dp(host, 10);
        root.addView(results, rlp);

        scroll.addView(root);
        updateModeStyles();
        return scroll;
    }

    private TextView modeButton(String text, boolean place) {
        TextView t = Ui.button(host, text, Ui.SURFACE2, Ui.MUTED, v -> setMode(place));
        return t;
    }

    private void setMode(boolean place) {
        placeMode = place;
        updateModeStyles();
        if (input != null) {
            input.setHint(place ? "地点/地标/公交站，如 天安门、韶关东站" : "线路号，如 38、101（马坝⇔梅村）");
        }
        if (hint != null) {
            hint.setText(place
                    ? "搜索地点或公交站，选择后按当前位置推荐直达乘车路线"
                    : "在 " + app.cityName() + " 的线路表里搜索，离线也能用（首次需联网下载线路表）");
        }
        if (results != null) results.removeAllViews();
        lastPlaces = null;
    }

    private void updateModeStyles() {
        if (lineTab == null) return;
        lineTab.setBackground(Ui.round(placeMode ? Ui.SURFACE2 : Ui.ACCENT_DIM, Ui.dp(host, 10)));
        lineTab.setTextColor(placeMode ? Ui.MUTED : Ui.ACCENT);
        placeTab.setBackground(Ui.round(placeMode ? Ui.ACCENT_DIM : Ui.SURFACE2, Ui.dp(host, 10)));
        placeTab.setTextColor(placeMode ? Ui.ACCENT : Ui.MUTED);
    }

    private void doSearch(String keyword) {
        final String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            toast(placeMode ? "请输入地点或公交站名" : "请输入线路号或线路名");
            return;
        }
        if (searching || recommending) return;
        searching = true;
        hint.setText("正在搜索「" + kw + "」…");
        if (placeMode) {
            app.run(() -> app.api.searchPlaces(kw), places -> {
                searching = false;
                lastPlaces = places;
                if (places == null || places.count() == 0) {
                    hint.setText("没有找到「" + kw + "」相关的地点或公交站");
                    results.removeAllViews();
                    return;
                }
                hint.setText("找到 " + places.pois.size() + " 个地点 · " + places.stations.size()
                        + " 个公交站 · " + places.lines.size() + " 条相关线路");
                renderPlaces(places);
            }, error -> {
                searching = false;
                hint.setText("搜索失败：" + friendly(error));
            });
            return;
        }
        final String cityId = app.cityId();
        app.run(() -> app.searchLines(kw, cityId), lines -> {
            searching = false;
            hint.setText("在 " + app.cityName() + " 找到 " + lines.size() + " 条线路");
            renderLines(lines);
        }, error -> {
            searching = false;
            hint.setText("搜索失败：" + friendly(error));
        });
    }

    private void renderPlaces(Models.PlaceSearch places) {
        results.removeAllViews();
        if (places == null) return;
        if (!places.lines.isEmpty()) {
            section("相关线路");
            for (int i = 0; i < Math.min(10, places.lines.size()); i++) {
                renderLineCard(places.lines.get(i), null);
            }
        }
        if (!places.pois.isEmpty()) {
            section("地点");
            for (int i = 0; i < Math.min(10, places.pois.size()); i++) {
                Models.Poi poi = places.pois.get(i);
                double[] wgs = poiWgs(poi, places.gpsType);
                int dist = Geo.distance(app.lat(), app.lng(), wgs[0], wgs[1]);
                String sub = poi.address.isEmpty() ? poi.sn : poi.address;
                if (!poi.sn1Tag.isEmpty()) sub = poi.sn1Tag + " · " + sub;
                renderPlaceCard(poi.title(), sub, dist, "地点", wgs[0], wgs[1], poi.title());
            }
        }
        if (!places.stations.isEmpty()) {
            section("公交站");
            for (int i = 0; i < Math.min(12, places.stations.size()); i++) {
                Models.PlaceStation st = places.stations.get(i);
                double[] sw = gpsToWgs(st.lat, st.lng, st.gpsType);
                int dist = Geo.distance(app.lat(), app.lng(), sw[0], sw[1]);
                String sub = st.stopNum > 0 ? st.stopNum + " 条线路经过" : "公交站";
                renderPlaceCard(st.sn, sub, dist, "公交站", sw[0], sw[1], st.sn);
            }
        }
    }

    private void section(String title) {
        TextView tv = Ui.text(host, title, 13, Ui.MUTED, true);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, results.getChildCount() == 0 ? 2 : 10);
        lp.bottomMargin = Ui.dp(host, 4);
        results.addView(tv, lp);
    }

    private void renderPlaceCard(String title, String subtitle, int distance, String kind,
                                 double lat, double lng, String name) {
        LinearLayout card = Ui.card(host);
        card.setClickable(true);
        Ui.setRipple(card);
        card.setOnClickListener(v -> recommend(new Target(name, lat, lng)));
        LinearLayout top = Ui.row(host);
        top.addView(Ui.text(host, title, 16, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (distance >= 0) top.addView(Ui.chip(host, Geo.formatDistance(distance), Ui.SURFACE2, Ui.MUTED));
        card.addView(top);
        TextView sub = Ui.text(host, kind + " · " + subtitle, 12, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 4);
        card.addView(sub, lp);
        LinearLayout.LayoutParams clp = Ui.matchWrap();
        clp.bottomMargin = Ui.dp(host, 10);
        results.addView(card, clp);
    }

    private void renderLineCard(Models.Line line, String extra) {
        LinearLayout card = Ui.card(host);
        card.setClickable(true);
        Ui.setRipple(card);
        card.setOnClickListener(v -> host.push(new LineScreen(host, line, 0)));
        LinearLayout row = Ui.row(host);
        TextView name = Ui.text(host, LineScreen.displayName(line), 17, Ui.TEXT, true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!line.firstTime.isEmpty() || !line.lastTime.isEmpty()) {
            row.addView(Ui.chip(host, line.firstTime + "-" + line.lastTime, Ui.SURFACE2, Ui.MUTED));
        }
        card.addView(row);
        String dir = (line.startSn == null ? "" : line.startSn) + "  →  " + (line.endSn == null ? "" : line.endSn);
        if (extra != null && !extra.isEmpty()) dir = extra + " · " + dir;
        TextView d = Ui.text(host, dir, 12, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 4);
        card.addView(d, lp);
        LinearLayout.LayoutParams clp = Ui.matchWrap();
        clp.bottomMargin = Ui.dp(host, 10);
        results.addView(card, clp);
    }

    private void renderLines(List<Models.Line> lines) {
        results.removeAllViews();
        for (Models.Line line : lines) renderLineCard(line, null);
    }

    private void recommend(Target target) {
        if (recommending) return;
        recommending = true;
        hint.setText("正在查找从当前位置到「" + target.name + "」的乘车方案…");
        final String cityId = app.cityId();
        final double olat = app.lat(), olng = app.lng();
        app.run(() -> {
            Models.NearLines origin = app.api.nearLines(olat, olng, cityId);
            return buildOptions(origin, olat, olng, target.lat, target.lng);
        }, options -> {
            recommending = false;
            renderOptions(target, options);
        }, error -> {
            recommending = false;
            hint.setText("路线推荐失败：" + friendly(error));
        });
    }

    private List<RouteOption> buildOptions(Models.NearLines origin,
                                           double olat, double olng, double dlat, double dlng) {
        Map<String, RouteOption> best = new HashMap<>();
        Map<String, Boolean> seen = new HashMap<>();
        if (origin == null) return new ArrayList<>();
        for (Models.NearGroup og : origin.groups) {
            for (Models.NearLine ol : og.lines) {
                if (ol.line == null || ol.line.lineId.isEmpty() || seen.containsKey(ol.line.lineId)) continue;
                seen.put(ol.line.lineId, Boolean.TRUE);
                Models.Route route;
                try {
                    route = app.api.lineRoute(ol.line.lineId, app.cityId());
                } catch (Exception e) {
                    continue;
                }
                if (route == null || route.stations.isEmpty()) continue;
                int bi = -1, ai = -1;
                int bd = Integer.MAX_VALUE, ad = Integer.MAX_VALUE;
                for (int i = 0; i < route.stations.size(); i++) {
                    Models.Stop st = route.stations.get(i);
                    double[] rp = routePoint(route, st);
                    if (rp[0] == 0 || rp[1] == 0) continue;
                    int db = Geo.distance(olat, olng, rp[0], rp[1]);
                    if (db < bd) { bd = db; bi = i; }
                    int da = Geo.distance(dlat, dlng, rp[0], rp[1]);
                    if (da < ad) { ad = da; ai = i; }
                }
                if (bi < 0 || ai < 0 || bd > 1500 || ad > 1500) continue;
                Models.Stop board = route.stations.get(bi);
                Models.Stop alight = route.stations.get(ai);
                if (board.order <= 0 || alight.order <= board.order) continue;
                RouteOption r = new RouteOption();
                r.line = route.line == null || route.line.lineId.isEmpty() ? ol.line : route.line;
                r.boardName = board.sn.isEmpty() ? og.sn : board.sn;
                r.alightName = alight.sn;
                r.boardOrder = board.order;
                r.alightOrder = alight.order;
                r.walkBoard = bd;
                r.walkAlight = ad;
                String key = ol.line.lineId;
                RouteOption old = best.get(key);
                if (old == null || r.score() < old.score()) best.put(key, r);
            }
        }
        List<RouteOption> out = new ArrayList<>(best.values());
        out.sort((a, b) -> Integer.compare(a.score(), b.score()));
        if (out.size() > 6) return new ArrayList<>(out.subList(0, 6));
        return out;
    }

    private double[] routePoint(Models.Route route, Models.Stop s) {
        if ("wgs".equalsIgnoreCase(route.gpsType)) return new double[]{s.lat, s.lng};
        return Geo.stationToWgs(s);
    }

    private double[] groupWgs(Models.NearGroup g) {
        if (g.targetStation != null && g.targetStation.bestLat() != 0 && g.targetStation.bestLng() != 0) {
            return new double[]{g.targetStation.bestLat(), g.targetStation.bestLng()};
        }
        return Geo.bd09ToWgs84(g.lat, g.lng);
    }

    private double[] poiWgs(Models.Poi poi, String gpsType) {
        return gpsToWgs(poi.lat, poi.lng, gpsType);
    }

    private double[] gpsToWgs(double lat, double lng, String gpsType) {
        if ("gcj".equalsIgnoreCase(gpsType)) return Geo.gcj02ToWgs84(lat, lng);
        if ("bd".equalsIgnoreCase(gpsType)) return Geo.bd09ToWgs84(lat, lng);
        return new double[]{lat, lng};
    }

    private void renderOptions(Target target, List<RouteOption> options) {
        results.removeAllViews();
        hint.setText(options == null || options.isEmpty()
                ? "没有找到从当前位置到「" + target.name + "」的直达方案"
                : "找到 " + options.size() + " 条从当前位置到「" + target.name + "」的直达方案");
        TextView back = Ui.button(host, "← 返回搜索结果", Ui.SURFACE2, Ui.ACCENT, v -> renderPlaces(lastPlaces));
        results.addView(back, Ui.matchWrap());
        section("从当前位置到「" + target.name + "」");
        if (options == null || options.isEmpty()) {
            LinearLayout card = Ui.card(host);
            card.addView(Ui.text(host, "暂未找到直达公交方案", 16, Ui.TEXT, true));
            TextView sub = Ui.text(host, "可能是附近站点或目的地周边线路没有重叠。可以换一个更具体的地点名，或先在「附近」页查看当前站点的全部线路。", 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 6);
            card.addView(sub, lp);
            results.addView(card, Ui.matchWrap());
            return;
        }
        for (RouteOption o : options) {
            LinearLayout card = Ui.card(host);
            card.setClickable(true);
            Ui.setRipple(card);
            card.setOnClickListener(v -> host.push(new LineScreen(host, o.line, o.boardOrder, o.boardName, o.alightOrder, o.alightName)));
            LinearLayout top = Ui.row(host);
            top.addView(Ui.text(host, LineScreen.displayName(o.line), 17, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.chip(host, "直达", Ui.ACCENT_DIM, Ui.ACCENT));
            card.addView(top);
            TextView route = Ui.text(host, "步行 " + Geo.formatDistance(o.walkBoard) + " → " + o.boardName + " 上车\n"
                    + "乘 " + (o.alightOrder - o.boardOrder) + " 站 → " + o.alightName + " 下车\n"
                    + "步行 " + Geo.formatDistance(o.walkAlight) + " 到达 " + target.name, 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 5);
            card.addView(route, lp);
            TextView open = Ui.text(host, "点击查看实时到站 →", 11, Ui.ACCENT, true);
            LinearLayout.LayoutParams olp = Ui.matchWrap();
            olp.topMargin = Ui.dp(host, 6);
            card.addView(open, olp);
            LinearLayout.LayoutParams clp = Ui.matchWrap();
            clp.bottomMargin = Ui.dp(host, 10);
            results.addView(card, clp);
        }
        TextView note = Ui.text(host, "推荐按步行距离和乘坐站数排序，仅供参考 · 实时到站以线路页为准", 11, Ui.FAINT, false);
        results.addView(note, Ui.matchWrap());
    }

    private String friendly(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().isEmpty()) return "未知错误";
        return e.getMessage();
    }

    private static final class Target {
        final String name;
        final double lat;
        final double lng;

        Target(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static final class RouteOption {
        Models.Line line;
        String boardName = "";
        String alightName = "";
        int boardOrder;
        int alightOrder;
        int walkBoard;
        int walkAlight;

        int score() {
            return walkBoard + walkAlight + (alightOrder - boardOrder) * 120;
        }
    }
}
