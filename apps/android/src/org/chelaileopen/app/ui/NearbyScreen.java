package org.chelaileopen.app.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Format;
import org.chelaileopen.app.core.Geo;
import org.chelaileopen.app.core.Models;

import java.util.List;

/** 首页：当前城市 + 附近站点与线路实时到站。 */
public class NearbyScreen extends Screen {
    private LinearLayout list;
    private TextView updatedView;
    private TextView cityView;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable ticker;
    private boolean loading;
    private boolean visible;
    private boolean locating;

    public NearbyScreen(MainActivity host) {
        super(host);
    }

    @Override
    public String title() {
        return "附近";
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 24));

        // 城市 + 定位
        LinearLayout bar = Ui.row(host);
        cityView = Ui.text(host, app.cityName() + " · 附近", 20, Ui.TEXT, true);
        bar.addView(cityView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView locate = Ui.button(host, "定位", Ui.SURFACE2, Ui.ACCENT, v -> doLocate());
        bar.addView(locate);
        root.addView(bar);

        updatedView = Ui.text(host, "正在加载附近站点…", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams upLp = Ui.matchWrap();
        upLp.topMargin = Ui.dp(host, 4);
        root.addView(updatedView, upLp);
        root.addView(Ui.space(host, 10));

        list = Ui.column(host);
        root.addView(list);

        TextView footer = Ui.text(host, "数据来源：车来了公开 H5 接口 · 本应用为开源学习项目，与官方无关", 11, Ui.FAINT, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fLp = Ui.matchWrap();
        fLp.topMargin = Ui.dp(host, 18);
        root.addView(footer, fLp);

        scroll.addView(root);
        return scroll;
    }

    private void doLocate() {
        locateAndLoad(true);
    }

    /** 首次启动自动定位；之后只在用户点“定位”时重新获取。 */
    private void maybeAutoLocate() {
        if (app.hasGeo() || app.autoLocateTried() || !visible) return;
        app.markAutoLocateTried();
        locateAndLoad(false);
    }

    private void locateAndLoad(final boolean manual) {
        if (locating) return;
        locating = true;
        if (manual) toast("正在定位…");
        LocationHelper.request(host, new LocationHelper.Callback() {
            @Override
            public void onLocation(double lat, double lng) {
                locating = false;
                app.setGeo(lat, lng);
                if (manual) {
                    toast("已定位：" + String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng));
                }
                resolveCityAndLoad(lat, lng, manual);
            }

            @Override
            public void onFail(String message) {
                locating = false;
                if (manual) {
                    toast(message);
                } else {
                    updatedView.setText("自动定位未成功：" + message + " · 可在设置中手动选择城市");
                    updatedView.setTextColor(Ui.MUTED);
                    load(false);
                }
            }
        });
    }

    /** 定位成功后反查城市，匹配城市列表并切换，再拉附近线路。 */
    private void resolveCityAndLoad(final double lat, final double lng, final boolean manual) {
        app.run(() -> {
            String cityName = app.api.locateCity(lat, lng);
            if (cityName == null || cityName.isEmpty()) return null;
            List<Models.City> cities = app.cachedCities();
            if (cities == null) {
                cities = app.api.cities();
                app.cacheCities(cities);
            }
            for (Models.City c : cities) {
                if (c.cityName.equals(cityName)) return c;
            }
            return null;
        }, city -> {
            if (city != null) {
                boolean changed = !city.cityId.equals(app.cityId());
                app.setCity(city.cityId, city.cityName);
                cityView.setText(city.cityName + " · 附近");
                if (changed || manual) toast("定位到 " + city.cityName);
            } else if (manual) {
                toast("没有匹配到城市，请手动选择");
            }
            load(true);
        }, error -> {
            if (manual) toast("定位城市失败：" + error.getMessage());
            load(true);
        });
    }

    @Override
    public void onResume() {
        visible = true;
        load(false);
        startTicker();
        handler.postDelayed(this::maybeAutoLocate, 700);
    }

    @Override
    public void onPause() {
        visible = false;
        stopTicker();
    }

    private void startTicker() {
        stopTicker();
        ticker = new Runnable() {
            @Override
            public void run() {
                if (!visible) return;
                load(false);
                handler.postDelayed(this, Math.max(10, app.refreshSeconds()) * 1000L);
            }
        };
        handler.postDelayed(ticker, Math.max(10, app.refreshSeconds()) * 1000L);
    }

    private void stopTicker() {
        if (ticker != null) handler.removeCallbacks(ticker);
        ticker = null;
    }

    private void load(boolean manual) {
        if (loading) return;
        loading = true;
        if (manual) updatedView.setText("正在刷新…");
        final double lat = app.lat();
        final double lng = app.lng();
        final String cityId = app.cityId();
        app.run(() -> app.api.nearLines(lat, lng, cityId), result -> {
            loading = false;
            cityView.setText(app.cityName() + " · 附近");
            updatedView.setText("更新于 " + Format.nowClock() + " · 每 " + app.refreshSeconds() + " 秒自动刷新");
            render(result);
        }, error -> {
            loading = false;
            updatedView.setText("加载失败：" + friendly(error) + " · 点按重试");
            updatedView.setTextColor(Ui.RED);
            updatedView.setOnClickListener(v -> {
                updatedView.setTextColor(Ui.MUTED);
                load(true);
            });
        });
    }

    private String friendly(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "网络不可用";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void render(Models.NearLines near) {
        list.removeAllViews();
        if (near == null || near.groups.isEmpty()) {
            LinearLayout card = Ui.card(host);
            card.addView(Ui.text(host, "附近没有查到线路", 15, Ui.TEXT, true));
            TextView hint = Ui.text(host, "可能当前城市不在实时数据覆盖范围。可以到「设置」切换城市，或点「定位」重新获取位置。", 13, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 6);
            card.addView(hint, lp);
            list.addView(card);
            return;
        }
        int shown = 0;
        for (Models.NearGroup g : near.groups) {
            if (g.lines.isEmpty()) continue;
            list.addView(groupCard(g));
            View sp = Ui.space(host, 10);
            list.addView(sp);
            if (++shown >= 12) break;
        }
        if (shown == 0) {
            LinearLayout card = Ui.card(host);
            card.addView(Ui.text(host, "附近站点还没有实时数据", 15, Ui.TEXT, true));
            card.addView(Ui.text(host, "深夜 0-5 点多数线路没有发车，白天再来看看。", 13, Ui.MUTED, false));
            list.addView(card);
        }
    }

    /** 附近分组只有 sId，站点详情需要 physicalStId，这里就近换一次。 */
    private void openStation(Models.NearGroup g) {
        if (g.sId == null || g.sId.isEmpty()) {
            toast("该站点没有详情");
            return;
        }
        final double lat;
        final double lng;
        if (g.lat != 0 && g.lng != 0) {
            double[] gcj = Geo.bd09ToGcj02(g.lat, g.lng);
            double[] w = Geo.gcj02ToWgs84(gcj[0], gcj[1]);
            lat = w[0];
            lng = w[1];
        } else {
            lat = app.lat();
            lng = app.lng();
        }
        app.run(() -> app.api.nearStations(lat, lng, app.cityId()), result -> {
            Models.NearStation hit = null;
            for (Models.NearStation st : result.stations) {
                if (g.sId.equals(st.sId)) {
                    hit = st;
                    break;
                }
            }
            if (hit == null || hit.physicalStId.isEmpty()) {
                toast("该站点没有详情");
                return;
            }
            host.push(new StationScreen(host, hit.physicalStId, hit.sn.isEmpty() ? g.sn : hit.sn, app.cityId()));
        }, error -> toast("打开站点失败：" + friendly(error)));
    }

    private View groupCard(Models.NearGroup g) {
        LinearLayout card = Ui.card(host);
        card.setClickable(true);
        Ui.setRipple(card);
        card.setOnClickListener(v -> openStation(g));

        LinearLayout head = Ui.row(host);
        String name = g.sn == null || g.sn.isEmpty() ? "附近站点" : g.sn;
        TextView nameView = Ui.text(host, name, 16, Ui.TEXT, true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String dist = Geo.formatDistance(g.distance);
        head.addView(Ui.chip(host, dist.isEmpty() ? "附近" : dist, Ui.SURFACE2, Ui.MUTED));
        card.addView(head);

        for (Models.NearLine n : g.lines) {
            View row = lineRow(n);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 10);
            card.addView(row, lp);
        }
        return card;
    }

    private View lineRow(Models.NearLine n) {
        LinearLayout row = Ui.row(host);
        row.setClickable(true);
        Ui.setRipple(row);
        row.setOnClickListener(v -> host.push(new LineScreen(host, n.line, n.targetStation.order)));

        LinearLayout left = Ui.column(host);
        TextView name = Ui.text(host, n.line.name, 15, Ui.TEXT, true);
        left.addView(name);
        String dest = n.line.endSn == null || n.line.endSn.isEmpty() ? "" : "开往 " + n.line.endSn;
        if (!dest.isEmpty()) {
            TextView d = Ui.text(host, dest, 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 2);
            left.addView(d, lp);
        }
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String text = Format.arrival(n);
        int color = text.contains("分钟") || text.contains("即将") ? Ui.GREEN : Ui.MUTED;
        TextView eta = Ui.text(host, text, 14, color, true);
        eta.setGravity(Gravity.RIGHT);
        row.addView(eta);
        return row;
    }
}
