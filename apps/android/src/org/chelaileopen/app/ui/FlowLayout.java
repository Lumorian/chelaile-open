package org.chelaileopen.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * 简易流式布局：子 View 按行排列，超过可用宽度自动换行。
 * 发车时刻表的班次 chip 数量不固定，用 LinearLayout 横排会把最后一个 chip 挤成竖排。
 */
public class FlowLayout extends ViewGroup {
    private final int hGap;
    private final int vGap;

    public FlowLayout(Context c, int hGapDp, int vGapDp) {
        super(c);
        hGap = Ui.dp(c, hGapDp);
        vGap = Ui.dp(c, vGapDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int avail = widthSize - getPaddingLeft() - getPaddingRight();
        if (avail <= 0) avail = Integer.MAX_VALUE / 2;

        int x = 0, y = 0, rowHeight = 0, maxWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > 0 && x + cw > avail) {
                x = 0;
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            x += cw + hGap;
            rowHeight = Math.max(rowHeight, ch);
            maxWidth = Math.max(maxWidth, x - hGap);
        }
        int totalH = y + rowHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(maxWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(totalH, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int avail = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > getPaddingLeft() && x + cw > getPaddingLeft() + avail) {
                x = getPaddingLeft();
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            int left = x + lp.leftMargin;
            int top = y + lp.topMargin;
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
            x += cw + hGap;
            rowHeight = Math.max(rowHeight, ch);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams ? p : new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
