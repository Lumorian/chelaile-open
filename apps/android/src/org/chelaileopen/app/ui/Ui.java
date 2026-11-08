package org.chelaileopen.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 纯代码 UI 的小工具箱：配色、圆角卡片、文本、按钮。 */
public final class Ui {
    public static int BG = 0xFF0E1116;
    public static int SURFACE = 0xFF161B22;
    public static int SURFACE2 = 0xFF1B2330;
    public static int TEXT = 0xFFE6EDF3;
    public static int MUTED = 0xFF8B949E;
    public static int FAINT = 0xFF6E7681;
    public static int ACCENT = 0xFF3B82F6;
    public static int ACCENT_DIM = 0xFF17304F;
    public static int GREEN = 0xFF2ECC71;
    public static int ORANGE = 0xFFF5A524;
    public static int RED = 0xFFEF4444;
    public static int LINE = 0xFF2D333B;
    public static int TRACK = 0xFF243040;
    public static int DOT_PASSED = 0xFF3A4150;
    public static int DOT_FUTURE = 0xFF2F6DB5;

    private static boolean lightMode;
    private static float fontScale = 1f;

    private Ui() {
    }

    /** App 启动或设置变化时调用；所有页面需在 Activity 重建后重新构建。 */
    public static void configure(boolean light, float scale) {
        lightMode = light;
        fontScale = scale <= 0f ? 1f : scale;
        if (light) {
            BG = 0xFFF5F7FA;
            SURFACE = 0xFFFFFFFF;
            SURFACE2 = 0xFFE9EFF6;
            TEXT = 0xFF17202A;
            MUTED = 0xFF5B6675;
            FAINT = 0xFF8A94A3;
            ACCENT = 0xFF1D64D8;
            ACCENT_DIM = 0xFFE3EFFD;
            GREEN = 0xFF11805E;
            ORANGE = 0xFFB45309;
            RED = 0xFFC62828;
            LINE = 0xFFDCE3EB;
            TRACK = 0xFFD5E0EC;
            DOT_PASSED = 0xFFB3BFCE;
            DOT_FUTURE = 0xFF7DA6D8;
        } else {
            BG = 0xFF0E1116;
            SURFACE = 0xFF161B22;
            SURFACE2 = 0xFF1B2330;
            TEXT = 0xFFE6EDF3;
            MUTED = 0xFF8B949E;
            FAINT = 0xFF6E7681;
            ACCENT = 0xFF3B82F6;
            ACCENT_DIM = 0xFF17304F;
            GREEN = 0xFF2ECC71;
            ORANGE = 0xFFF5A524;
            RED = 0xFFEF4444;
            LINE = 0xFF2D333B;
            TRACK = 0xFF243040;
            DOT_PASSED = 0xFF3A4150;
            DOT_FUTURE = 0xFF2F6DB5;
        }
    }

    public static boolean isLight() {
        return lightMode;
    }

    public static float fontScale() {
        return fontScale;
    }

    /** 把设计稿 sp 换算成当前字号倍数。 */
    public static float sp(Context c, float sp) {
        return sp * fontScale;
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(c, sp));
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static FlowLayout flow(Context c) {
        return new FlowLayout(c, 6, 6);
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, float radiusPx, int strokeWidthPx) {
        GradientDrawable d = round(fill, radiusPx);
        d.setStroke(strokeWidthPx, strokeColor);
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(round(SURFACE, dp(c, 14)));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static void gap(View v, int bottomDp) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(v.getContext(), bottomDp);
        v.setLayoutParams(lp);
    }

    public static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static TextView button(Context c, String label, int bg, int fg, View.OnClickListener click) {
        TextView t = text(c, label, 14, fg, true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(round(bg, dp(c, 10)));
        int ph = dp(c, 16), pv = dp(c, 10);
        t.setPadding(ph, pv, ph, pv);
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(click);
        return t;
    }

    public static TextView chip(Context c, String label, int bg, int fg) {
        TextView t = text(c, label, 11, fg, false);
        t.setBackground(round(bg, dp(c, 8)));
        int ph = dp(c, 7), pv = dp(c, 3);
        t.setPadding(ph, pv, ph, pv);
        return t;
    }

    public static void setRipple(View v) {
        TypedValue out = new TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            v.setForeground(v.getContext().getDrawable(out.resourceId));
        }
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static void divider(Context c, LinearLayout parent) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        parent.addView(v, lp);
    }
}
