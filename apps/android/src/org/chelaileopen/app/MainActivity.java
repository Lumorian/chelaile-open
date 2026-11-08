package org.chelaileopen.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.chelaileopen.app.ui.FavoritesScreen;
import org.chelaileopen.app.ui.LocationHelper;
import org.chelaileopen.app.ui.NearbyScreen;
import org.chelaileopen.app.ui.Screen;
import org.chelaileopen.app.ui.SearchScreen;
import org.chelaileopen.app.ui.SettingsScreen;
import org.chelaileopen.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private FrameLayout content;
    private TextView titleView;
    private TextView backView;
    private LinearLayout nav;
    private final List<Screen> stack = new ArrayList<>();
    private int currentTab = 0;
    private final TextView[] tabViews = new TextView[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);
        buildLayout();
        LocationHelper.attach(this);
        switchTab(0);
    }

    private void buildLayout() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        // 顶栏
        LinearLayout header = Ui.row(this);
        header.setBackgroundColor(Ui.SURFACE);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        backView = Ui.text(this, "‹", 30, Ui.TEXT, false);
        backView.setGravity(Gravity.CENTER);
        backView.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 2));
        backView.setOnClickListener(v -> pop());
        header.addView(backView, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        titleView = Ui.text(this, "", 18, Ui.TEXT, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = Ui.dp(this, 6);
        header.addView(titleView, titleLp);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.BG);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部导航
        nav = Ui.row(this);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        String[] labels = {"附近", "搜索", "关注", "设置"};
        String[] icons = {"◎", "⌕", "★", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            TextView icon = Ui.text(this, icons[i], 18, Ui.MUTED, false);
            icon.setGravity(Gravity.CENTER);
            TextView label = Ui.text(this, labels[i], 11, Ui.MUTED, false);
            label.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(label);
            final int index = i;
            item.setOnClickListener(v -> switchTab(index));
            tabViews[i] = label;
            item.setTag(icon);
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Screen createTab(int index) {
        switch (index) {
            case 1:
                return new SearchScreen(this);
            case 2:
                return new FavoritesScreen(this);
            case 3:
                return new SettingsScreen(this);
            default:
                return new NearbyScreen(this);
        }
    }

    public void switchTab(int index) {
        if (!stack.isEmpty()) stack.get(stack.size() - 1).onPause();
        stack.clear();
        currentTab = index;
        stack.add(createTab(index));
        render();
    }

    public void push(Screen screen) {
        if (!stack.isEmpty()) stack.get(stack.size() - 1).onPause();
        stack.add(screen);
        render();
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(140).start();
    }

    public void pop() {
        if (!stack.isEmpty() && stack.get(stack.size() - 1).onBackPressed()) return;
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1).onPause();
            render();
        } else if (currentTab != 0) {
            switchTab(0);
        }
    }

    private void render() {
        Screen top = stack.get(stack.size() - 1);
        content.removeAllViews();
        content.addView(top.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        titleView.setText(top.title());
        boolean canBack = stack.size() > 1;
        backView.setVisibility(canBack ? View.VISIBLE : View.INVISIBLE);
        for (int i = 0; i < 4; i++) {
            boolean active = i == currentTab && stack.size() == 1;
            tabViews[i].setTextColor(active ? Ui.ACCENT : Ui.MUTED);
            ((TextView) nav.getChildAt(i).getTag()).setTextColor(active ? Ui.ACCENT : Ui.MUTED);
        }
        top.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LocationHelper.onPermissionResult(this, requestCode, grantResults);
        if (!stack.isEmpty()) {
            stack.get(stack.size() - 1).onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        if (!stack.isEmpty() && stack.get(stack.size() - 1).onBackPressed()) return;
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1).onPause();
            render();
            return;
        }
        if (currentTab != 0) {
            switchTab(0);
            return;
        }
        super.onBackPressed();
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

}
