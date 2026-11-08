package org.chelaileopen.app.ui;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Models;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 设置：城市、定位、刷新间隔、历史记录、关于。 */
public class SettingsScreen extends Screen {
    private TextView cityValue;
    private TextView refreshValue;
    private TextView historyValue;

    public SettingsScreen(MainActivity host) {
        super(host);
    }

    @Override
    public String title() {
        return "设置";
    }

    @Override
    protected View build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 28));

        LinearLayout cityCard = Ui.card(host);
        cityCard.addView(Ui.text(host, "城市", 15, Ui.TEXT, true));
        cityValue = Ui.text(host, app.cityName(), 13, Ui.MUTED, false);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 4);
        cityCard.addView(cityValue, lp);
        LinearLayout cityRow = Ui.row(host);
        cityRow.setPadding(0, Ui.dp(host, 10), 0, 0);
        TextView pick = Ui.button(host, "切换城市", Ui.SURFACE2, Ui.ACCENT, v -> showCityDialog());
        TextView locate = Ui.button(host, "用当前位置", Ui.SURFACE2, Ui.TEXT, v -> locateCity());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = Ui.dp(host, 8);
        cityRow.addView(pick, p1);
        cityRow.addView(locate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cityCard.addView(cityRow);
        root.addView(cityCard);
        root.addView(Ui.space(host, 10));

        LinearLayout refreshCard = Ui.card(host);
        refreshCard.addView(Ui.text(host, "自动刷新间隔", 15, Ui.TEXT, true));
        refreshValue = Ui.text(host, app.refreshSeconds() + " 秒", 13, Ui.MUTED, false);
        LinearLayout.LayoutParams rlp = Ui.matchWrap();
        rlp.topMargin = Ui.dp(host, 4);
        refreshCard.addView(refreshValue, rlp);
        TextView change = Ui.button(host, "修改", Ui.SURFACE2, Ui.ACCENT, v -> showRefreshDialog());
        LinearLayout.LayoutParams clp = Ui.matchWrap();
        clp.topMargin = Ui.dp(host, 10);
        refreshCard.addView(change, clp);
        root.addView(refreshCard);
        root.addView(Ui.space(host, 10));

        LinearLayout historyCard = Ui.card(host);
        historyCard.addView(Ui.text(host, "本机历史到站", 15, Ui.TEXT, true));
        historyValue = Ui.text(host, "用于在没有实时数据时给出历史到站参考；数据只存在这台手机上，不会上传。", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams hlp = Ui.matchWrap();
        hlp.topMargin = Ui.dp(host, 4);
        historyCard.addView(historyValue, hlp);
        TextView clear = Ui.button(host, "清空历史记录", Ui.SURFACE2, Ui.RED, v -> confirmClearHistory());
        LinearLayout.LayoutParams clp2 = Ui.matchWrap();
        clp2.topMargin = Ui.dp(host, 10);
        historyCard.addView(clear, clp2);
        root.addView(historyCard);
        root.addView(Ui.space(host, 10));

        LinearLayout about = Ui.card(host);
        about.addView(Ui.text(host, "关于", 15, Ui.TEXT, true));
        TextView body = Ui.text(host, "车来了 Open 0.2.2\n"
                + "一个开源的公交到站客户端，复刻自车来了 Android 版 7.6.8 的公开 H5 接口。\n\n"
                + "· 数据来源：web.chelaile.net.cn 的公开接口，版权归原公司所有\n"
                + "· 本项目仅用于学习与研究，不与官方关联，请勿商用\n"
                + "· 代码以 MIT 协议开源\n"
                + "· 无广告、无统计、无账号", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams blp = Ui.matchWrap();
        blp.topMargin = Ui.dp(host, 6);
        about.addView(body, blp);
        root.addView(about);

        TextView version = Ui.text(host, "version 0.2.2 · build 4", 11, Ui.FAINT, false);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = Ui.matchWrap();
        vlp.topMargin = Ui.dp(host, 16);
        root.addView(version, vlp);

        scroll.addView(root);
        return scroll;
    }

    @Override
    public void onResume() {
        cityValue.setText(app.cityName());
        refreshValue.setText(app.refreshSeconds() + " 秒");
        app.run(() -> app.history.totalSamples("", 0), n -> {
        }, e -> {
        });
    }

    private void showCityDialog() {
        List<Models.City> cached = app.cachedCities();
        if (cached != null) {
            showCityList(cached);
            return;
        }
        toast("正在加载城市列表…");
        app.run(() -> app.api.cities(), cities -> {
            app.cacheCities(cities);
            showCityList(cities);
        }, error -> toast("加载城市列表失败：" + error.getMessage()));
    }

    private void showCityList(List<Models.City> cities) {
        LinearLayout box = Ui.column(host);
        box.setPadding(Ui.dp(host, 16), Ui.dp(host, 8), Ui.dp(host, 16), Ui.dp(host, 8));
        EditText filter = new EditText(host);
        filter.setHint("搜索城市名或拼音");
        filter.setHintTextColor(Ui.FAINT);
        filter.setTextColor(Ui.TEXT);
        filter.setSingleLine(true);
        box.addView(filter, Ui.matchWrap());
        ListView listView = new ListView(host);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 360)));

        List<String> names = new ArrayList<>();
        for (Models.City c : cities) names.add(c.cityName);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String kw = s.toString().trim().toLowerCase(Locale.ROOT);
                adapter.clear();
                for (Models.City c : cities) {
                    if (kw.isEmpty() || c.cityName.contains(kw) || c.pinyin.toLowerCase(Locale.ROOT).startsWith(kw)) {
                        adapter.add(c.cityName);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("选择城市")
                .setView(box)
                .setNegativeButton("取消", null)
                .create();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String name = adapter.getItem(position);
            for (Models.City c : cities) {
                if (c.cityName.equals(name)) {
                    app.setCity(c.cityId, c.cityName);
                    cityValue.setText(c.cityName);
                    toast("已切换到 " + c.cityName);
                    break;
                }
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void locateCity() {
        toast("正在定位…");
        LocationHelper.request(host, new LocationHelper.Callback() {
            @Override
            public void onLocation(double lat, double lng) {
                app.setGeo(lat, lng);
                toast("定位成功，正在匹配城市…");
                app.run(() -> app.api.locateCity(lat, lng), cityName -> {
                    if (cityName == null || cityName.isEmpty()) {
                        toast("没有匹配到城市，请手动选择");
                        return;
                    }
                    findAndSetCity(cityName);
                }, error -> toast("定位城市失败：" + error.getMessage()));
            }

            @Override
            public void onFail(String message) {
                toast(message);
            }
        });
    }

    private void findAndSetCity(String name) {
        List<Models.City> cached = app.cachedCities();
        if (cached != null) {
            applyCity(cached, name);
            return;
        }
        app.run(() -> app.api.cities(), cities -> {
            app.cacheCities(cities);
            applyCity(cities, name);
        }, error -> toast("加载城市列表失败"));
    }

    private void applyCity(List<Models.City> cities, String name) {
        for (Models.City c : cities) {
            if (c.cityName.equals(name)) {
                app.setCity(c.cityId, c.cityName);
                cityValue.setText(c.cityName);
                toast("已切换到 " + c.cityName);
                return;
            }
        }
        toast("城市列表里没有「" + name + "」");
    }

    private void showRefreshDialog() {
        final int[] values = {10, 15, 30, 60};
        String[] labels = {"10 秒", "15 秒", "30 秒", "60 秒"};
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == app.refreshSeconds()) checked = i;
        new AlertDialog.Builder(host)
                .setTitle("自动刷新间隔")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    app.setRefreshSeconds(values[which]);
                    refreshValue.setText(values[which] + " 秒");
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(host)
                .setTitle("清空历史到站记录？")
                .setMessage("只会删除这台手机上的记录，无法恢复。")
                .setPositiveButton("清空", (dialog, which) -> app.run(() -> {
                    app.history.clearAll();
                    return app.history.totalSamples("", 0);
                }, n -> toast("已清空历史记录"), error -> toast("清空失败")))
                .setNegativeButton("取消", null)
                .show();
    }
}
