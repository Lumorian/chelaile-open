package org.chelaileopen.app.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Models;

import java.util.List;

/** 搜索：按线路号/线路名在本地线路表里查。 */
public class SearchScreen extends Screen {
    private EditText input;
    private LinearLayout results;
    private TextView hint;
    private boolean searching;

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

        LinearLayout bar = Ui.row(host);
        input = new EditText(host);
        input.setHint("线路号，如 38、101（马坝⇔梅村）");
        input.setHintTextColor(Ui.FAINT);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(15);
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

        hint = Ui.text(host, "在 " + app.cityName() + " 的线路表里搜索，离线也能用（首次需联网下载线路表）", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams hlp = Ui.matchWrap();
        hlp.topMargin = Ui.dp(host, 10);
        root.addView(hint, hlp);

        results = Ui.column(host);
        LinearLayout.LayoutParams rlp = Ui.matchWrap();
        rlp.topMargin = Ui.dp(host, 10);
        root.addView(results, rlp);

        scroll.addView(root);
        return scroll;
    }

    private void doSearch(String keyword) {
        final String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            toast("请输入线路号或线路名");
            return;
        }
        if (searching) return;
        searching = true;
        hint.setText("正在搜索「" + kw + "」…");
        final String cityId = app.cityId();
        app.run(() -> app.searchLines(kw, cityId), lines -> {
            searching = false;
            hint.setText("在 " + app.cityName() + " 找到 " + lines.size() + " 条线路");
            render(lines);
        }, error -> {
            searching = false;
            hint.setText("搜索失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
        });
    }

    private void render(List<Models.Line> lines) {
        results.removeAllViews();
        for (Models.Line line : lines) {
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
            TextView d = Ui.text(host, dir, 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 4);
            card.addView(d, lp);
            LinearLayout.LayoutParams clp = Ui.matchWrap();
            clp.bottomMargin = Ui.dp(host, 10);
            results.addView(card, clp);
        }
    }
}
