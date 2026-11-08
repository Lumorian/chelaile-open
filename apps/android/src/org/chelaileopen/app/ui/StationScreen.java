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

/** 站点详情：经过本站的所有线路与到站时间。 */
public class StationScreen extends Screen {
    private final String physicalStId;
    private final String stationName;
    private final String cityId;
    private LinearLayout list;
    private TextView subtitle;
    private boolean loading;

    public StationScreen(MainActivity host, String physicalStId, String stationName, String cityId) {
        super(host);
        this.physicalStId = physicalStId;
        this.stationName = stationName == null ? "" : stationName;
        this.cityId = cityId;
    }

    @Override
    public String title() {
        return stationName.isEmpty() ? "站点" : stationName;
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 28));

        LinearLayout head = Ui.card(host);
        head.addView(Ui.text(host, stationName.isEmpty() ? "站点" : stationName, 22, Ui.TEXT, true));
        subtitle = Ui.text(host, "正在加载经过本站的线路…", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 4);
        head.addView(subtitle, lp);
        root.addView(head);
        root.addView(Ui.space(host, 10));

        list = Ui.column(host);
        root.addView(list);
        scroll.addView(root);
        return scroll;
    }

    @Override
    public void onResume() {
        load();
    }

    private void load() {
        if (loading) return;
        loading = true;
        app.run(() -> app.api.stationDetail(physicalStId, stationName, cityId), detail -> {
            loading = false;
            render(detail);
        }, error -> {
            loading = false;
            subtitle.setText("加载失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
        });
    }

    private void render(Models.StationDetail detail) {
        list.removeAllViews();
        if (detail == null || detail.entries.isEmpty()) {
            subtitle.setText("这里没有线路数据");
            return;
        }
        subtitle.setText("共 " + detail.entries.size() + " 个站台 · 点线路查看实时到站");
        int platform = 0;
        for (Models.StationEntry entry : detail.entries) {
            if (entry.lines.isEmpty()) continue;
            platform++;
            LinearLayout card = Ui.card(host);
            String dist = entry.distance > 0 ? Geo.formatDistance((int) entry.distance) : "";
            LinearLayout top = Ui.row(host);
            top.addView(Ui.text(host, "站台 " + platform, 13, Ui.MUTED, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!dist.isEmpty()) top.addView(Ui.chip(host, dist, Ui.SURFACE2, Ui.MUTED));
            else top.addView(Ui.chip(host, entry.lines.size() + " 条线路", Ui.SURFACE2, Ui.MUTED));
            card.addView(top);
            card.addView(Ui.space(host, 6));
            for (Models.StationLine sl : entry.lines) {
                LinearLayout row = Ui.row(host);
                row.setClickable(true);
                Ui.setRipple(row);
                row.setOnClickListener(v -> host.push(new LineScreen(host, sl.line, sl.targetStation.order)));
                LinearLayout left = Ui.column(host);
                left.addView(Ui.text(host, LineScreen.displayName(sl.line), 15, Ui.TEXT, true));
                String dest = sl.line.endSn == null || sl.line.endSn.isEmpty() ? "" : "开往 " + sl.line.endSn;
                if (!dest.isEmpty()) left.addView(Ui.text(host, dest, 12, Ui.MUTED, false));
                row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                String eta = Format.arrival(sl);
                TextView etaView = Ui.text(host, eta, 14, eta.contains("分钟") || eta.contains("即将") ? Ui.GREEN : Ui.MUTED, true);
                etaView.setGravity(Gravity.RIGHT);
                row.addView(etaView);
                LinearLayout.LayoutParams rlp = Ui.matchWrap();
                rlp.topMargin = Ui.dp(host, 10);
                card.addView(row, rlp);
            }
            LinearLayout.LayoutParams clp = Ui.matchWrap();
            clp.bottomMargin = Ui.dp(host, 10);
            list.addView(card, clp);
        }
    }
}
