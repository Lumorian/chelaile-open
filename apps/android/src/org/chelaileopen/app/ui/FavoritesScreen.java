package org.chelaileopen.app.ui;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Format;
import org.chelaileopen.app.core.Models;
import org.json.JSONArray;
import org.json.JSONObject;


/** 关注：收藏的线路，进入时顺带取一次预测。 */
public class FavoritesScreen extends Screen {
    private LinearLayout list;
    private TextView hint;
    private boolean loading;

    public FavoritesScreen(MainActivity host) {
        super(host);
    }

    @Override
    public String title() {
        return "关注";
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 28));
        hint = Ui.text(host, "长按可取消关注", 12, Ui.MUTED, false);
        root.addView(hint);
        list = Ui.column(host);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 10);
        root.addView(list, lp);
        scroll.addView(root);
        return scroll;
    }

    @Override
    public void onResume() {
        render();
    }

    private void render() {
        list.removeAllViews();
        JSONArray arr = app.favorites();
        if (arr.length() == 0) {
            LinearLayout card = Ui.card(host);
            card.addView(Ui.text(host, "还没有关注任何线路", 15, Ui.TEXT, true));
            TextView sub = Ui.text(host, "打开一条线路，点右上角「☆ 收藏」，下次就能在这里一屏看完到站时间。", 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 6);
            card.addView(sub, lp);
            list.addView(card);
            return;
        }
        hint.setText("共 " + arr.length() + " 条 · 长按可取消关注 · 进入后自动刷新");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Models.Line line = new Models.Line();
            line.lineId = o.optString("lineId");
            line.name = o.optString("name");
            line.direction = o.optInt("direction", 0);
            line.startSn = o.optString("startSn");
            line.endSn = o.optString("endSn");
            String cityId = o.optString("cityId", app.cityId());

            LinearLayout card = Ui.card(host);
            LinearLayout row = Ui.row(host);
            LinearLayout left = Ui.column(host);
            left.addView(Ui.text(host, LineScreen.displayName(line), 18, Ui.TEXT, true));
            String dir = (line.startSn == null ? "" : line.startSn) + " → " + (line.endSn == null ? "" : line.endSn);
            if (!dir.trim().equals("→")) left.addView(Ui.text(host, dir, 12, Ui.MUTED, false));
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView eta = Ui.text(host, "…", 14, Ui.MUTED, true);
            eta.setGravity(Gravity.RIGHT);
            row.addView(eta);
            card.addView(row);

            card.setOnClickListener(v -> host.push(new LineScreen(host, line, 0)));
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(host)
                        .setTitle("取消关注 " + LineScreen.displayName(line) + "？")
                        .setPositiveButton("取消关注", (d, w) -> {
                            app.removeFavorite(line.lineId, line.direction);
                            render();
                        })
                        .setNegativeButton("保留", null)
                        .show();
                return true;
            });
            list.addView(card);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.bottomMargin = Ui.dp(host, 10);
            card.setLayoutParams(lp);

            loadEta(line, cityId, eta);
        }
    }

    private void loadEta(Models.Line line, String cityId, TextView eta) {
        if (loading) return;
        app.run(() -> app.api.lineDetail(line.lineId, line.name, line.direction, "", "", line.name, 0, cityId),
                detail -> {
                    if (detail == null) {
                        eta.setText("暂无数据");
                        return;
                    }
                    String text = Format.primary(detail);
                    eta.setText(text);
                    eta.setTextColor(text.contains("分钟") || text.contains("站") ? Ui.GREEN : Ui.MUTED);
                },
                error -> eta.setText("加载失败"));
    }
}
