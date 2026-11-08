package org.chelaileopen.app.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 把接口里的零散字段拼成用户能看懂的一句话。 */
public final class Format {
    private Format() {
    }

    /** 附近线路的“还有多久”。 */
    public static String arrival(Models.NearLine n) {
        if (n == null) return "";
        for (Models.StnState s : n.stnStates) {
            String text = stateText(s);
            if (!text.isEmpty()) return text;
        }
        if (n.depIntervalM > 0) return "约 " + n.depIntervalM + " 分钟/班";
        if (n.line != null && n.line.shortDesc != null && !n.line.shortDesc.isEmpty()) return n.line.shortDesc;
        return "暂无实时";
    }

    /** 站点详情里某条线的“还有多久”。 */
    public static String arrival(Models.StationLine s) {
        if (s == null) return "";
        for (Models.StnState st : s.stnStates) {
            String text = stateText(st);
            if (!text.isEmpty()) return text;
        }
        if (s.depIntervalM > 0) return "约 " + s.depIntervalM + " 分钟/班";
        return "暂无实时";
    }

    /** 线路详情的主预测：优先真实到站，其次发车描述/预测文案。 */
    public static String primary(Models.Detail d) {
        if (d == null) return "";
        Models.Bus bus = nearestBus(d);
        if (bus != null && bus.order > 0 && d.targetOrder > 0) {
            int stopsLeft = d.targetOrder - bus.order;
            if (stopsLeft < 0) return d.depDesc.isEmpty() ? "车辆已过站" : "等待下一班";
            if (stopsLeft == 0) return "车辆即将到站";
            return "还有 " + stopsLeft + " 站";
        }
        if (!d.depDesc.isEmpty()) return d.depDesc;
        String pred = d.predictionText.replace("\n", " ").trim();
        if (!pred.isEmpty() && !pred.equals("发车 时间") && !pred.equals("发车时间")) return pred;
        if (d.depIntervalM > 0) return "约 " + d.depIntervalM + " 分钟/班";
        if (!d.preArrivalTime.isEmpty()) return d.preArrivalTime;
        return "暂无实时数据";
    }

    public static Models.Bus nearestBus(Models.Detail d) {
        Models.Bus best = null;
        for (Models.Bus b : d.buses) {
            if (b.order < d.targetOrder) {
                if (best == null || b.order > best.order) best = b;
            }
        }
        if (best == null && !d.buses.isEmpty()) best = d.buses.get(0);
        return best;
    }

    /** 把一条 stnState 变成 "3 站 · 6 分钟" 这样的文案；无实时数据时返回空串。 */
    public static String stateText(Models.StnState s) {
        if (s == null) return "";
        String stops = s.stops > 0 ? s.stops + " 站" : "";
        String minutes = "";
        if (s.time >= 0) {
            minutes = s.time <= 0 ? "即将到站" : s.time + " 分钟";
        } else if (s.seconds >= 0) {
            int m = (s.seconds + 59) / 60;
            minutes = m <= 0 ? "即将到站" : m + " 分钟";
        } else if (s.arrivalTime > 0) {
            long left = (s.arrivalTime - System.currentTimeMillis()) / 1000L;
            int m = (int) ((left + 59) / 60);
            minutes = m <= 0 ? "即将到站" : m + " 分钟";
        }
        if (!stops.isEmpty() && !minutes.isEmpty()) return stops + " · " + minutes;
        if (!stops.isEmpty()) return stops;
        return minutes;
    }

    public static String nowClock() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    public static String minutesText(int seconds) {
        if (seconds < 60) return seconds + " 秒";
        return (seconds / 60) + " 分钟";
    }

    public static String operationWindow(Models.Line line) {
        if (line == null) return "";
        if (!line.segTimes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String[] seg : line.segTimes) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(seg[0]).append("-").append(seg[1]);
            }
            return sb.toString();
        }
        if (!line.firstTime.isEmpty() || !line.lastTime.isEmpty()) return line.firstTime + "-" + line.lastTime;
        return "";
    }
}
