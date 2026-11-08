package org.chelaileopen.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.core.Format;
import org.chelaileopen.app.core.Geo;
import org.chelaileopen.app.core.Models;
import org.chelaileopen.app.data.HistoryStore;
import org.chelaileopen.app.service.ReminderService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 线路详情：实时预测、发车时刻表、车辆位置、时间轴、本机历史到站、到站提醒。 */
public class LineScreen extends Screen {
    private final Models.Line line;
    private int targetOrder;
    private String preferStationName;
    private boolean preferApplied;
    /** 推荐方案的下车站（0 表示不是从推荐卡进入）。 */
    private int alightOrder;
    private String alightName = "";
    private LinearLayout planBox;

    private Models.Detail detail;
    private Models.Timetable timetable;
    private String summary = "";
    /** 按当前 GPS 对所有站点算出的最近站（不是服务端返回的 nearStnOrder 缓存）。 */
    private int nearestOrder;
    private int nearestDistance = -1;
    private Models.Stop pendingReminderStop;
    private int pendingReminderAdvance;
    private String pendingReminderKind;

    private ScrollView scroll;
    private LinearLayout root;
    private TextView nameView;
    private TextView directionView;
    private TextView metaView;
    private TextView etaView;
    private TextView etaSubView;
    private LinearLayout etaCard;
    private LinearLayout summaryBox;
    private LinearLayout timetableBox;
    private LinearLayout busesBox;
    private LinearLayout timelineBox;
    private LinearLayout historyBox;
    private TextView updatedView;

    private boolean loading;
    private boolean loadedOnce;
    private boolean retrying;
    private boolean timetableRequested;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private boolean visible;
    private final Map<String, Integer> lastSeen = new HashMap<>();

    public LineScreen(MainActivity host, Models.Line line, int targetOrder) {
        super(host);
        this.line = line;
        this.targetOrder = Math.max(0, targetOrder);
    }

    public LineScreen(MainActivity host, Models.Line line, int targetOrder, String preferStationName) {
        this(host, line, targetOrder);
        this.preferStationName = preferStationName;
    }

    public LineScreen(MainActivity host, Models.Line line, int targetOrder, String preferStationName,
                      int alightOrder, String alightName) {
        this(host, line, targetOrder, preferStationName);
        this.alightOrder = Math.max(0, alightOrder);
        this.alightName = alightName == null ? "" : alightName;
    }

    @Override
    public String title() {
        return displayName(line);
    }

    static String displayName(Models.Line line) {
        if (line == null) return "线路";
        String n = line.name == null ? "" : line.name;
        if (n.isEmpty()) return "线路";
        // 线路名本身可能已经带“路/线”（如 1路区间、快速公交1线），只给纯数字补“路”。
        if (n.contains("路") || n.contains("线")) return n;
        if (n.matches("^[0-9]+$") || n.matches("^[夜专快加急]?[0-9]+$")) return n + "路";
        return n;
    }

    @Override
    protected View build() {
        scroll = new ScrollView(host);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        root = Ui.column(host);
        root.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 28));

        root.addView(heroCard());
        root.addView(Ui.space(host, 10));

        etaCard = Ui.card(host);
        etaView = Ui.text(host, "正在获取实时信息…", 22, Ui.TEXT, true);
        etaCard.addView(etaView);
        etaSubView = Ui.text(host, "", 13, Ui.MUTED, false);
        LinearLayout.LayoutParams eLp = Ui.matchWrap();
        eLp.topMargin = Ui.dp(host, 6);
        etaCard.addView(etaSubView, eLp);
        updatedView = Ui.text(host, "", 11, Ui.FAINT, false);
        LinearLayout.LayoutParams uLp = Ui.matchWrap();
        uLp.topMargin = Ui.dp(host, 8);
        etaCard.addView(updatedView, uLp);
        root.addView(etaCard);
        root.addView(Ui.space(host, 10));

        summaryBox = Ui.card(host);
        summaryBox.setVisibility(View.GONE);
        root.addView(summaryBox);
        root.addView(Ui.space(host, 10));

        timetableBox = Ui.card(host);
        timetableBox.setVisibility(View.GONE);
        root.addView(timetableBox);
        root.addView(Ui.space(host, 10));

        busesBox = Ui.card(host);
        busesBox.setVisibility(View.GONE);
        root.addView(busesBox);
        root.addView(Ui.space(host, 10));

        timelineBox = Ui.card(host);
        root.addView(timelineBox);
        root.addView(Ui.space(host, 10));

        historyBox = Ui.card(host);
        historyBox.setVisibility(View.GONE);
        root.addView(historyBox);

        scroll.addView(root);
        return scroll;
    }

    private View heroCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout top = Ui.row(host);
        nameView = Ui.text(host, displayName(line), 26, Ui.TEXT, true);
        top.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView fav = Ui.button(host, app.isFavorite(line.lineId) ? "★ 已收藏" : "☆ 收藏", Ui.SURFACE2,
                app.isFavorite(line.lineId) ? Ui.ORANGE : Ui.MUTED, v -> toggleFavorite((TextView) v));
        top.addView(fav);
        card.addView(top);

        directionView = Ui.text(host, directionText(), 14, Ui.TEXT, false);
        LinearLayout.LayoutParams dLp = Ui.matchWrap();
        dLp.topMargin = Ui.dp(host, 6);
        card.addView(directionView, dLp);

        metaView = Ui.text(host, metaText(), 12, Ui.MUTED, false);
        LinearLayout.LayoutParams mLp = Ui.matchWrap();
        mLp.topMargin = Ui.dp(host, 4);
        card.addView(metaView, mLp);

        planBox = Ui.column(host);
        planBox.setBackground(Ui.round(Ui.ACCENT_DIM, Ui.dp(host, 10)));
        planBox.setPadding(Ui.dp(host, 10), Ui.dp(host, 9), Ui.dp(host, 10), Ui.dp(host, 9));
        planBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = Ui.matchWrap();
        plp.topMargin = Ui.dp(host, 10);
        card.addView(planBox, plp);

        LinearLayout actions = Ui.row(host);
        LinearLayout.LayoutParams aLp = Ui.matchWrap();
        aLp.topMargin = Ui.dp(host, 12);
        TextView swap = Ui.button(host, "⇄ 换向", Ui.SURFACE2, Ui.TEXT, v -> switchDirection());
        TextView remind = Ui.button(host, "⏰ 到站提醒", Ui.ACCENT_DIM, Ui.ACCENT, v -> showReminderDialog());
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r1.rightMargin = Ui.dp(host, 8);
        actions.addView(swap, r1);
        actions.addView(remind, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(actions, aLp);
        return card;
    }

    private String directionText() {
        String s = line.startSn == null ? "" : line.startSn;
        String e = line.endSn == null ? "" : line.endSn;
        if (s.isEmpty() && e.isEmpty()) return "";
        return s + "  →  " + e;
    }

    private String metaText() {
        List<String> parts = new ArrayList<>();
        if (!line.firstTime.isEmpty() || !line.lastTime.isEmpty()) parts.add("首末班 " + line.firstTime + "-" + line.lastTime);
        if (!line.price.isEmpty()) parts.add("票价 " + line.price);
        if (line.stationsNum > 0) parts.add(line.stationsNum + " 站");
        return String.join(" · ", parts);
    }

    @Override
    public void onResume() {
        visible = true;
        refresh();
        startTicker();
    }

    @Override
    public void onPause() {
        visible = false;
        stopTicker();
    }

    private void startTicker() {
        stopTicker();
        int sec = detail != null && detail.refreshInterval > 0 ? detail.refreshInterval : Math.max(10, app.refreshSeconds());
        ticker = new Runnable() {
            @Override
            public void run() {
                if (!visible) return;
                refresh();
                int next = detail != null && detail.refreshInterval > 0 ? detail.refreshInterval : Math.max(10, app.refreshSeconds());
                handler.postDelayed(this, next * 1000L);
            }
        };
        handler.postDelayed(ticker, sec * 1000L);
    }

    private void stopTicker() {
        if (ticker != null) handler.removeCallbacks(ticker);
        ticker = null;
    }

    private Models.Stop stopAt(int order) {
        if (detail == null) return null;
        for (Models.Stop s : detail.stops) if (s.order == order) return s;
        return null;
    }

    /**
     * 统一“你在这”：用应用当前坐标对全部站点求几何最近。
     * lineDetail/附近接口的 nearStnOrder 可能来自旧定位或服务端缓存的用户位置，
     * 不同线路刷新时机不同会造成同一位置显示不同站，所以这里以本机 GPS 为准。
     */
    private int nearestOrder(Models.Detail d) {
        if (d == null || d.stops.isEmpty()) return 0;
        double lat = app.lat();
        double lng = app.lng();
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (Models.Stop s : d.stops) {
            double[] p = Geo.stationToWgs(s);
            if (p[0] == 0 && p[1] == 0) continue;
            int meters = Geo.distance(lat, lng, p[0], p[1]);
            if (meters >= 0 && meters < bestDistance) {
                bestDistance = meters;
                best = s.order;
            }
        }
        if (best > 0) {
            nearestOrder = best;
            nearestDistance = bestDistance;
            return best;
        }
        nearestDistance = -1;
        nearestOrder = d.nearStnOrder > 0 ? d.nearStnOrder : d.targetOrder;
        return nearestOrder;
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        final String cityId = app.cityId();
        final int order = targetOrder;
        final Models.Stop target = stopAt(order);
        String stationName = target == null ? "" : target.sn;
        String nextName = "";
        Models.Stop next = stopAt(order + 1);
        if (next != null) nextName = next.sn;
        final String sn = stationName;
        final String nn = nextName;
        final String lineName = line.name;
        final int direction = line.direction;
        app.run(() -> app.api.lineDetail(line.lineId, lineName, direction, sn, nn, line.lineNo, order, cityId),
                result -> {
                    loading = false;
                    loadedOnce = true;
                    retrying = false;
                    if (result == null) {
                        etaView.setText("没有取到线路数据");
                        etaSubView.setText("可能线路已调整，返回重新搜索试试");
                        return;
                    }
                    detail = result;
                    if (preferStationName != null && !preferApplied) {
                        preferApplied = true;
                        for (Models.Stop s : result.stops) {
                            if (s.sn.equals(preferStationName)) {
                                if (s.order != targetOrder) {
                                    targetOrder = s.order;
                                    renderDetail();
                                    refresh();
                                    return;
                                }
                                break;
                            }
                        }
                    }
                    if (targetOrder <= 0 && result.targetOrder > 0) targetOrder = result.targetOrder;
                    if (result.nearStnOrder > 0) {
                        // 服务端认为离用户最近的是哪一站
                    }
                    renderDetail();
                    recordHistory(result);
                    maybeLoadTimetable(result);
                    maybeLoadSummary();
                },
                error -> {
                    loading = false;
                    if (!retrying) {
                        retrying = true;
                        etaView.setText("网络波动，正在重试…");
                        etaSubView.setText(friendly(error));
                        updatedView.setText("正在重新连接");
                        handler.postDelayed(this::refresh, 900);
                        return;
                    }
                    retrying = false;
                    etaView.setText("加载失败");
                    etaSubView.setText(friendly(error));
                    updatedView.setText("下拉返回重试");
                });
    }

    private String friendly(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "网络不可用，请检查连接";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void recordHistory(Models.Detail d) {
        final String lineId = line.lineId;
        final int direction = line.direction;
        for (Models.Bus b : d.buses) {
            Integer prev = lastSeen.get(b.busId);
            if (prev != null && b.order > prev && prev > 0) {
                final int from = prev;
                final int to = b.order;
                final String busId = b.busId;
                final List<Models.Stop> stops = d.stops;
                app.io.execute(() -> app.history.recordPassages(lineId, direction, from, to, stops, busId));
            }
            lastSeen.put(b.busId, b.order);
        }
    }

    private void renderDetail() {
        Models.Detail d = detail;
        if (d == null) return;

        Models.Line l = d.line != null && !d.line.name.isEmpty() ? d.line : line;
        nameView.setText(displayName(l));
        directionView.setText((l.startSn == null ? "" : l.startSn) + "  →  " + (l.endSn == null ? "" : l.endSn));
        String meta = metaText();
        if (d.line != null) {
            List<String> parts = new ArrayList<>();
            if (!d.line.firstTime.isEmpty() || !d.line.lastTime.isEmpty()) parts.add("首末班 " + d.line.firstTime + "-" + d.line.lastTime);
            if (!d.line.price.isEmpty()) parts.add("票价 " + d.line.price);
            if (!d.stops.isEmpty()) parts.add(d.stops.size() + " 站");
            meta = String.join(" · ", parts);
        }
        metaView.setText(meta);

        etaCard.setVisibility(View.VISIBLE);
        etaView.setText(Format.primary(d));
        etaView.setTextColor(predictionColor(d));
        List<String> sub = new ArrayList<>();
        String pred = d.predictionText.replace("\n", " ").trim();
        if (!pred.isEmpty() && !pred.equals(etaView.getText().toString())
                && !pred.equals("发车 时间") && !pred.equals("发车时间")) {
            sub.add(pred);
        }
        Models.Bus nearest = Format.nearestBus(d);
        boolean passed = nearest != null && d.targetOrder > 0 && nearest.order > d.targetOrder;
        if (passed && !d.depDesc.isEmpty() && !sub.contains(d.depDesc)) sub.add(d.depDesc);
        if (d.depIntervalM > 0) sub.add("发车间隔约 " + d.depIntervalM + " 分钟");
        if (!d.preArrivalTime.isEmpty()) sub.add("预计 " + d.preArrivalTime);
        etaSubView.setText(String.join(" · ", sub));
        updatedView.setText("更新于 " + Format.nowClock() + (d.realData ? " · 实时" : " · 非实时（参考发车信息）"));

        refreshPlanCard(d);
        renderBuses(d);
        renderTimeline(d);
        renderHistory(d);
    }

    /** 从地点搜索的推荐卡进入时，在顶部展示上/下车站。 */
    private void refreshPlanCard(Models.Detail d) {
        planBox.removeAllViews();
        if (alightOrder <= 0) {
            planBox.setVisibility(View.GONE);
            return;
        }
        Models.Stop board = stopAt(targetOrder);
        Models.Stop alight = stopAt(alightOrder);
        if (board == null || alight == null) {
            planBox.setVisibility(View.GONE);
            return;
        }
        planBox.setVisibility(View.VISIBLE);
        planBox.addView(Ui.text(host, "推荐乘车 · 在 " + board.sn + " 上车", 12, Ui.ACCENT, true));
        TextView body = Ui.text(host, "乘 " + Math.max(1, alightOrder - targetOrder) + " 站，在 " + alight.sn + " 下车", 13, Ui.TEXT, false);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 3);
        planBox.addView(body, lp);
    }

    private int predictionColor(Models.Detail d) {
        Models.Bus bus = Format.nearestBus(d);
        if (bus != null && bus.order > 0 && d.targetOrder > 0) {
            int left = d.targetOrder - bus.order;
            if (left < 0) return Ui.MUTED;
            if (left == 0) return Ui.ORANGE;
            if (left <= 2) return Ui.ORANGE;
            return Ui.GREEN;
        }
        return Ui.TEXT;
    }

    private void renderBuses(Models.Detail d) {
        busesBox.removeAllViews();
        if (d.buses.isEmpty()) {
            busesBox.setVisibility(View.GONE);
            return;
        }
        busesBox.setVisibility(View.VISIBLE);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.text(host, "车辆位置", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, d.buses.size() + " 辆在路上", Ui.SURFACE2, Ui.MUTED));
        busesBox.addView(head);

        for (Models.Bus b : d.buses) {
            LinearLayout row = Ui.row(host);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 10);
            LinearLayout left = Ui.column(host);
            left.addView(Ui.text(host, "车辆 " + (b.busId.isEmpty() ? "—" : b.busId), 14, Ui.TEXT, true));
            Models.Stop nextStop = stopAt(b.order);
            String nextName = nextStop == null ? "" : nextStop.sn;
            String detailText;
            if (b.order > d.targetOrder) {
                detailText = "已过本站，正在开往 " + (nextName.isEmpty() ? "下一站" : nextName);
            } else if (b.order == d.targetOrder) {
                detailText = "正在驶向本站（你等的车）";
            } else {
                double dist = b.distanceToSc > 0 ? b.distanceToSc : b.distanceToWaitStn;
                detailText = "距 " + (nextName.isEmpty() ? "下一站" : nextName) + " " + (dist > 0 ? Geo.formatDistance((int) dist) : "—");
            }
            LinearLayout.LayoutParams tlp = Ui.matchWrap();
            tlp.topMargin = Ui.dp(host, 2);
            left.addView(Ui.text(host, detailText, 12, Ui.MUTED, false), tlp);
            if (!b.timeStr.isEmpty()) {
                left.addView(Ui.text(host, "定位时间 " + b.timeStr + (b.speed > 0 ? " · " + b.speed + " km/h" : ""), 11, Ui.FAINT, false));
            }
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            busesBox.addView(row, lp);
        }
        TextView note = Ui.text(host, "时间轴中的 🚌 表示车辆当前所在位置（下一站之前）", 11, Ui.FAINT, false);
        LinearLayout.LayoutParams nlp = Ui.matchWrap();
        nlp.topMargin = Ui.dp(host, 8);
        busesBox.addView(note, nlp);
    }

    private void renderTimeline(Models.Detail d) {
        timelineBox.removeAllViews();
        LinearLayout head = Ui.row(host);
        head.addView(Ui.text(host, "线路时间轴", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "共 " + d.stops.size() + " 站", Ui.SURFACE2, Ui.MUTED));
        timelineBox.addView(head);

        TextView legend = Ui.text(host, "左右滑动查看全部站点 · 点站点可设置上下车提醒\n"
                + "灰点=已过站 · 深蓝点=车辆下一站 · 绿点=你在这 · 橙点=下车站 · 浅蓝=未到 · 🚌=车辆位置", 11, Ui.FAINT, false);
        LinearLayout.LayoutParams llp = Ui.matchWrap();
        llp.topMargin = Ui.dp(host, 4);
        timelineBox.addView(legend, llp);

        Models.Bus bus = Format.nearestBus(d);
        int nearest = nearestOrder(d);
        final int cellWidth = Ui.dp(host, 96);
        final int cellHeight = Ui.dp(host, 158);

        HorizontalScrollView scroller = new HorizontalScrollView(host);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(Ui.dp(host, 2), 0, Ui.dp(host, 2), 0);

        LinearLayout track = Ui.row(host);
        track.setPadding(0, 0, 0, 0);
        int focusIndex = 0;
        int index = 0;
        for (Models.Stop st : d.stops) {
            boolean passed = bus != null && st.order < bus.order;
            boolean busNext = bus != null && st.order == bus.order;
            boolean isNearest = st.order == nearest;
            boolean isAlight = alightOrder > 0 && st.order == alightOrder;
            if (isNearest) focusIndex = index;
            View cell = timelineCell(st, passed, busNext, isNearest, isAlight, bus);
            track.addView(cell, new LinearLayout.LayoutParams(cellWidth, cellHeight));
            index++;
        }
        if (bus != null && bus.order > 0) {
            // 若没有匹配到任何站（数据异常），至少聚焦车辆位置附近。
            boolean found = false;
            for (Models.Stop st : d.stops) if (st.order == nearest) found = true;
            if (!found) focusIndex = Math.max(0, Math.min(d.stops.size() - 1, bus.order - 1));
        }
        scroller.addView(track, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = Ui.matchWrap();
        slp.topMargin = Ui.dp(host, 10);
        timelineBox.addView(scroller, slp);

        final int scrollTo = focusIndex;
        scroller.post(() -> {
            int x = scrollTo * cellWidth - scroller.getWidth() / 2 + cellWidth / 2;
            scroller.scrollTo(Math.max(0, x), 0);
        });
    }

    /** 横向时间轴的单个站点格：站名、轨道圆点、状态标签。 */
    private View timelineCell(Models.Stop s, boolean passed, boolean busNext, boolean nearest, boolean alight, Models.Bus bus) {
        LinearLayout cell = Ui.column(host);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(Ui.dp(host, 2), Ui.dp(host, 4), Ui.dp(host, 2), Ui.dp(host, 2));
        Ui.setRipple(cell);
        cell.setOnClickListener(v -> showStationReminderDialog(s));

        String topText = " ";
        int topColor = Ui.FAINT;
        if (busNext) {
            double dist = bus == null ? -1 : (bus.distanceToSc > 0 ? bus.distanceToSc : bus.distanceToWaitStn);
            topText = dist > 0 ? "🚌 距 " + Geo.formatDistance((int) dist) : "🚌 即将到站";
            topColor = Ui.ACCENT;
        } else if (nearest) {
            topText = "📍 你在这";
            topColor = Ui.GREEN;
        } else if (alight) {
            topText = "🎯 下车站";
            topColor = Ui.ORANGE;
        }
        TextView top = Ui.text(host, topText, 10, topColor, true);
        top.setGravity(Gravity.CENTER);
        top.setSingleLine(true);
        top.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 18)));

        TextView name = Ui.text(host, s.sn, 12, passed ? Ui.FAINT : Ui.TEXT, nearest || busNext);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 38)));

        FrameLayout rail = new FrameLayout(host);
        View line = new View(host);
        line.setBackgroundColor(passed ? Ui.LINE : Ui.TRACK);
        FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 2));
        lineLp.gravity = Gravity.CENTER_VERTICAL;
        rail.addView(line, lineLp);

        int color = dotColor(passed, busNext, nearest, alight);
        View dot = new View(host);
        int dotSize = nearest || busNext || alight ? Ui.dp(host, 15) : Ui.dp(host, 11);
        dot.setBackground(Ui.round(color, Ui.dp(host, 8)));
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSize, dotSize);
        dotLp.gravity = Gravity.CENTER;
        rail.addView(dot, dotLp);
        cell.addView(rail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 26)));

        if (nearest) {
            String dist = nearestDistance > 0 ? " · " + Geo.formatDistance(nearestDistance) : "";
            TextView chip = Ui.chip(host, "你在这" + dist, Ui.ACCENT_DIM, Ui.GREEN);
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (busNext) {
            TextView chip = Ui.chip(host, "车辆下一站", Ui.ACCENT_DIM, Ui.ACCENT);
            chip.setSingleLine(true);
            cell.addView(chip);
        } else if (alight) {
            TextView chip = Ui.chip(host, "下车站", Ui.SURFACE2, Ui.ORANGE);
            chip.setSingleLine(true);
            cell.addView(chip);
        } else {
            cell.addView(Ui.text(host, " ", 10, Ui.FAINT, false));
        }
        return cell;
    }

    private int dotColor(boolean passed, boolean busNext, boolean nearest, boolean alight) {
        if (busNext) return Ui.ACCENT;
        if (nearest) return Ui.GREEN;
        if (alight) return Ui.ORANGE;
        if (passed) return Ui.DOT_PASSED;
        return Ui.DOT_FUTURE;
    }

    private void renderHistory(Models.Detail d) {
        historyBox.removeAllViews();
        int order = nearestOrder(d);
        Models.Stop target = stopAt(order);
        String stationName = target == null ? "" : target.sn;
        historyBox.setVisibility(View.VISIBLE);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.text(host, "历史到站（本机记录）", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "仅本机", Ui.SURFACE2, Ui.MUTED));
        historyBox.addView(head);

        TextView sub = Ui.text(host, "根据你平时打开本页的记录，统计车辆经过「" + (stationName.isEmpty() ? "本站" : stationName) + "」的历史时刻。官方接口不提供历史到站。", 11, Ui.MUTED, false);
        LinearLayout.LayoutParams slp = Ui.matchWrap();
        slp.topMargin = Ui.dp(host, 4);
        historyBox.addView(sub, slp);

        final String lineId = line.lineId;
        final int direction = line.direction;
        final int stationOrder = order;
        app.run(() -> {
            List<HistoryStore.Bucket> buckets = app.history.buckets(lineId, direction, 30);
            List<HistoryStore.Bucket> mine = new ArrayList<>();
            for (HistoryStore.Bucket b : buckets) if (b.stationOrder == stationOrder) mine.add(b);
            return new Object[]{mine, app.history.totalSamples(lineId, direction)};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<HistoryStore.Bucket> mine = (List<HistoryStore.Bucket>) result[0];
            int total = (Integer) result[1];
            renderHistoryRows(mine, total, stationName);
        }, error -> renderHistoryRows(new ArrayList<>(), 0, stationName));
    }

    private void renderHistoryRows(List<HistoryStore.Bucket> buckets, int total, String stationName) {
        // 移除上次生成的动态部分（保留标题与说明，共 2 个子视图）
        while (historyBox.getChildCount() > 2) historyBox.removeViewAt(historyBox.getChildCount() - 1);
        if (buckets.isEmpty()) {
            TextView empty = Ui.text(host, total == 0
                    ? "还没有记录。多打开几次本线路，应用会自动记下车辆经过的站点与时刻。"
                    : "本站还没有积累到记录（全线已有 " + total + " 条）。", 12, Ui.FAINT, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 8);
            historyBox.addView(empty, lp);
            return;
        }
        LinearLayout list = Ui.column(host);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(host, 8);
        historyBox.addView(list, lp);
        int shown = 0;
        for (HistoryStore.Bucket b : buckets) {
            String clock = HistoryStore.formatClock(b.averageClock);
            LinearLayout row = Ui.row(host);
            TextView range = Ui.text(host, clockRange(b.bucketMinute), 12, Ui.MUTED, false);
            row.addView(range, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView avg = Ui.text(host, "平均 " + clock, 13, Ui.TEXT, true);
            row.addView(avg);
            TextView count = Ui.text(host, "  " + b.samples + " 次", 11, Ui.FAINT, false);
            row.addView(count);
            LinearLayout.LayoutParams rlp = Ui.matchWrap();
            rlp.topMargin = Ui.dp(host, 6);
            list.addView(row, rlp);
            if (++shown >= 8) break;
        }
        if (buckets.size() > shown) {
            list.addView(Ui.text(host, "还有 " + (buckets.size() - shown) + " 档未显示", 11, Ui.FAINT, false));
        }
    }

    private String clockRange(int bucketMinute) {
        int h = bucketMinute / 60;
        int m = bucketMinute % 60;
        return String.format(Locale.US, "%02d:%02d-%02d:%02d", h, m, (h + (m + 30) / 60) % 24, (m + 30) % 60);
    }

    private void maybeLoadTimetable(Models.Detail d) {
        if (timetableRequested) return;
        if (d.depTable != 1 && d.menuDepTable != 1) {
            timetableBox.setVisibility(View.GONE);
            return;
        }
        timetableRequested = true;
        final String lineId = line.lineId;
        final String lineName = line.name;
        final int direction = line.direction;
        final int order = d.targetOrder;
        app.run(() -> app.api.depTimeTable(lineId, lineName, direction, order), tt -> {
            timetable = tt;
            renderTimetable();
        }, error -> {
            timetableBox.setVisibility(View.GONE);
        });
    }

    private void renderTimetable() {
        timetableBox.removeAllViews();
        if (timetable == null || timetable.segments.isEmpty()) {
            timetableBox.setVisibility(View.GONE);
            return;
        }
        timetableBox.setVisibility(View.VISIBLE);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.text(host, "发车时刻表", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "首站", Ui.SURFACE2, Ui.MUTED));
        timetableBox.addView(head);
        if (!timetable.timeTip.isEmpty()) {
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 4);
            timetableBox.addView(Ui.text(host, timetable.timeTip, 11, Ui.MUTED, false), lp);
        }
        for (Models.TimetableSegment seg : timetable.segments) {
            LinearLayout segBox = Ui.column(host);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 10);
            timetableBox.addView(segBox, lp);

            String range = seg.fromTime + " - " + seg.toTime;
            LinearLayout row = Ui.row(host);
            row.addView(Ui.text(host, range, 13, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (seg.depInterval > 0) row.addView(Ui.chip(host, "约 " + seg.depInterval + " 分钟/班", Ui.SURFACE2, Ui.MUTED));
            else if (!seg.intervalTips.isEmpty()) row.addView(Ui.chip(host, seg.intervalTips, Ui.SURFACE2, Ui.MUTED));
            segBox.addView(row);

            if (!seg.times.isEmpty()) {
                FlowLayout chips = Ui.flow(host);
                chips.setPadding(0, Ui.dp(host, 6), 0, 0);
                for (String t : seg.times) {
                    chips.addView(Ui.chip(host, t, Ui.ACCENT_DIM, Ui.ACCENT));
                }
                segBox.addView(chips);
            }
            if (!seg.forecastDesc.isEmpty()) {
                LinearLayout.LayoutParams flp = Ui.matchWrap();
                flp.topMargin = Ui.dp(host, 6);
                segBox.addView(Ui.text(host, seg.forecastDesc, 12, Ui.GREEN, false), flp);
                applyTimetableForecast(seg.forecastDesc);
            }
        }
        if (!timetable.tip.isEmpty()) {
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 10);
            timetableBox.addView(Ui.text(host, timetable.tip, 11, Ui.FAINT, false), lp);
        }
    }


    /** 没有在途车辆时，用首站发车预测填补主预测。 */
    private void applyTimetableForecast(String desc) {
        Models.Detail d = detail;
        if (d == null) return;
        for (Models.Bus b : d.buses) {
            if (b.order > 0 && d.targetOrder > 0 && b.order <= d.targetOrder) return;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(desc);
        if (!m.find()) return;
        etaView.setText("下一班 " + m.group(1) + " 发车");
        etaView.setTextColor(Ui.ACCENT);
        String sub = etaSubView.getText().toString();
        if (!sub.contains("首站发车预测")) {
            etaSubView.setText(sub.isEmpty() ? "首站发车预测" : sub + " · 首站发车预测");
        }
    }

    private void maybeLoadSummary() {
        if (!summary.isEmpty() || summaryBox.getVisibility() == View.VISIBLE) return;
        final String lineId = line.lineId;
        final String lineName = line.name;
        final int direction = line.direction;
        app.run(() -> app.api.lineSummary(lineId, lineName, direction), text -> {
            summary = text == null ? "" : text.trim();
            if (summary.isEmpty()) return;
            summaryBox.removeAllViews();
            summaryBox.setVisibility(View.VISIBLE);
            summaryBox.addView(Ui.text(host, "线路信息", 15, Ui.TEXT, true));
            TextView body = Ui.text(host, summary, 12, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(host, 6);
            summaryBox.addView(body, lp);
        }, error -> {
        });
    }

    private void toggleFavorite(TextView button) {
        app.toggleFavorite(line, app.cityId());
        boolean fav = app.isFavorite(line.lineId);
        button.setText(fav ? "★ 已收藏" : "☆ 收藏");
        button.setTextColor(fav ? Ui.ORANGE : Ui.MUTED);
        toast(fav ? "已加入关注" : "已取消关注");
    }

    private void switchDirection() {
        toast("正在查找反向线路…");
        final String cityId = app.cityId();
        final String name = line.name;
        final String start = line.startSn;
        final String end = line.endSn;
        final String currentId = line.lineId;
        app.run(() -> {
            List<Models.Line> all = app.linesForCity(cityId);
            Models.Line best = null;
            for (Models.Line l : all) {
                if (!l.name.equals(name)) continue;
                if (l.lineId.equals(currentId)) continue;
                if (l.startSn.equals(end) && l.endSn.equals(start)) return l;
                if (best == null) best = l;
            }
            return best;
        }, reverse -> {
            if (reverse == null) {
                toast("没有找到反向线路");
                return;
            }
            String station = null;
            if (detail != null) {
                Models.Stop s = stopAt(nearestOrder(detail));
                if (s != null) station = s.sn;
            }
            host.push(new LineScreen(host, reverse, 0, station));
        }, error -> toast("查找反向线路失败：" + friendly(error)));
    }

    private void showReminderDialog() {
        if (detail == null) {
            toast("等线路数据加载完再设置提醒");
            return;
        }
        Models.Stop s = stopAt(nearestOrder(detail));
        if (s == null) {
            toast("没有找到你所在的站点");
            return;
        }
        showStationReminderDialog(s);
    }

    /** 任一站点都可设提醒：上车（到站提醒）或下车（提前 1 站）。 */
    private void showStationReminderDialog(Models.Stop s) {
        String[] items = new String[]{
                "车辆到站时提醒我（上车）",
                "提前 1 站提醒",
                "提前 2 站提醒",
                "提前 3 站提醒",
                "我要在这站下车（提前 1 站提醒）",
                "取消当前提醒"
        };
        new AlertDialog.Builder(host)
                .setTitle("提醒 · " + s.sn)
                .setItems(items, (dialog, which) -> {
                    if (which == 5) {
                        cancelReminder();
                        return;
                    }
                    if (which == 4) {
                        startReminder(s, 1, ReminderService.KIND_ALIGHT);
                    } else {
                        startReminder(s, which, ReminderService.KIND_BOARD);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void startReminder(Models.Stop s, int advance, String kind) {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && host.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingReminderStop = s;
            pendingReminderAdvance = advance;
            pendingReminderKind = kind;
            host.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 13);
            return;
        }
        launchReminder(s, advance, kind);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != 13) return;
        Models.Stop s = pendingReminderStop;
        int advance = pendingReminderAdvance;
        String kind = pendingReminderKind;
        pendingReminderStop = null;
        pendingReminderKind = null;
        if (s == null) return;
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchReminder(s, advance, kind);
        } else {
            toast("没有通知权限，无法发送到站提醒");
        }
    }

    private void launchReminder(Models.Stop s, int advance, String kind) {
        Intent intent = new Intent(host, ReminderService.class);
        intent.setAction(ReminderService.ACTION_START);
        intent.putExtra(ReminderService.EXTRA_LINE_ID, line.lineId);
        intent.putExtra(ReminderService.EXTRA_LINE_NAME, displayName(line));
        intent.putExtra(ReminderService.EXTRA_DIRECTION, line.direction);
        intent.putExtra(ReminderService.EXTRA_STATION_ORDER, s.order);
        intent.putExtra(ReminderService.EXTRA_STATION_NAME, s.sn);
        intent.putExtra(ReminderService.EXTRA_ADVANCE, advance);
        intent.putExtra(ReminderService.EXTRA_KIND, kind);
        intent.putExtra(ReminderService.EXTRA_CITY_ID, app.cityId());
        if (android.os.Build.VERSION.SDK_INT >= 26) host.startForegroundService(intent);
        else host.startService(intent);
        if (ReminderService.KIND_ALIGHT.equals(kind)) {
            toast("已开启下车提醒：" + s.sn + " 前 1 站");
        } else if (advance <= 0) {
            toast("已开启到站提醒：" + s.sn);
        } else {
            toast("已开启上车提醒：" + s.sn + " 前 " + advance + " 站");
        }
    }

    private void cancelReminder() {
        Intent stop = new Intent(host, ReminderService.class);
        stop.setAction(ReminderService.ACTION_STOP);
        host.startService(stop);
        toast("已取消提醒");
    }
}
