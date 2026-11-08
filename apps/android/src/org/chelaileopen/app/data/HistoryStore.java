package org.chelaileopen.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.chelaileopen.app.core.Models;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 本机历史到站记录：每次轮询看到车辆跨站，就记一条“某线路某站某时刻有车”。
 * 这类数据只存在用户手机上，不上传；用于在无实时预测时给出“历史平均到站时间”。
 */
public class HistoryStore extends SQLiteOpenHelper {
    public static class Bucket {
        public int stationOrder;
        public String stationName = "";
        public int bucketMinute;
        public double averageClock;
        public int samples;
    }

    public HistoryStore(Context context) {
        super(context, "history.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE passages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "line_id TEXT NOT NULL," +
                "direction INTEGER NOT NULL," +
                "station_order INTEGER NOT NULL," +
                "station_name TEXT," +
                "bus_id TEXT," +
                "ts INTEGER NOT NULL," +
                "clock_min INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_passages_line ON passages(line_id, direction, station_order, ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 目前只有 v1
    }

    /** 记录从 fromOrder（不含）到 toOrder（含）之间经过的站点。 */
    public void recordPassages(String lineId, int direction, int fromOrder, int toOrder, List<Models.Stop> stops, String busId) {
        if (toOrder <= fromOrder) return;
        Calendar cal = Calendar.getInstance();
        int clock = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        long ts = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Models.Stop s : stops) {
                if (s.order <= fromOrder || s.order > toOrder) continue;
                ContentValues v = new ContentValues();
                v.put("line_id", lineId);
                v.put("direction", direction);
                v.put("station_order", s.order);
                v.put("station_name", s.sn);
                v.put("bus_id", busId);
                v.put("ts", ts);
                v.put("clock_min", clock);
                db.insert("passages", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 按 30 分钟时间桶聚合最近 N 天的记录。 */
    public List<Bucket> buckets(String lineId, int direction, int days) {
        List<Bucket> out = new ArrayList<>();
        long since = System.currentTimeMillis() - days * 24L * 3600 * 1000;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT station_order, station_name, (clock_min/30)*30 AS bucket, AVG(clock_min), COUNT(*) " +
                        "FROM passages WHERE line_id=? AND direction=? AND ts>=? " +
                        "GROUP BY station_order, bucket ORDER BY station_order, bucket",
                new String[]{lineId, String.valueOf(direction), String.valueOf(since)});
        try {
            while (c.moveToNext()) {
                Bucket b = new Bucket();
                b.stationOrder = c.getInt(0);
                b.stationName = c.getString(1);
                b.bucketMinute = c.getInt(2);
                b.averageClock = c.getDouble(3);
                b.samples = c.getInt(4);
                out.add(b);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public int totalSamples(String lineId, int direction) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM passages WHERE line_id=? AND direction=?",
                new String[]{lineId, String.valueOf(direction)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public void clearAll() {
        getWritableDatabase().delete("passages", null, null);
    }

    public static String formatClock(double minutes) {
        int m = (int) Math.round(minutes);
        int h = (m / 60) % 24;
        int mm = m % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", h, mm);
    }
}
