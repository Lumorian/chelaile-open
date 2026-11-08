package org.chelaileopen.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.chelaileopen.app.App;
import org.chelaileopen.app.MainActivity;
import org.chelaileopen.app.R;
import org.chelaileopen.app.core.Api;
import org.chelaileopen.app.core.Models;

import java.util.Locale;

/**
 * 到站提醒：前台服务每 20 秒查询一次线路详情，当车辆离目标站只剩 N 站时发通知。
 * 只做本地查询，不上传任何信息。
 */
public class ReminderService extends Service {
    public static final String ACTION_START = "org.chelaileopen.app.action.REMIND_START";
    public static final String ACTION_STOP = "org.chelaileopen.app.action.REMIND_STOP";
    public static final String EXTRA_LINE_ID = "lineId";
    public static final String EXTRA_LINE_NAME = "lineName";
    public static final String EXTRA_DIRECTION = "direction";
    public static final String EXTRA_STATION_ORDER = "stationOrder";
    public static final String EXTRA_STATION_NAME = "stationName";
    public static final String EXTRA_ADVANCE = "advance";
    public static final String EXTRA_CITY_ID = "cityId";
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_BOARD = "board";
    public static final String KIND_ALIGHT = "alight";

    private static final String CHANNEL_ONGOING = "reminder_ongoing";
    private static final String CHANNEL_ALERT = "reminder_alert";
    private static final int ONGOING_ID = 1001;
    private static final int ALERT_ID = 1002;

    private volatile boolean running;
    private Thread worker;
    private String lineId = "";
    private String lineName = "";
    private String stationName = "";
    private String cityId = "241";
    private int direction;
    private int stationOrder;
    private int advance;
    private String kind = KIND_BOARD;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING, "到站提醒（监控中）", NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription("正在监控公交车辆的位置");
        ongoing.setShowBadge(false);
        nm.createNotificationChannel(ongoing);
        NotificationChannel alert = new NotificationChannel(CHANNEL_ALERT, "到站提醒（响铃）", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("车辆即将到站时提醒你");
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        lineId = intent.getStringExtra(EXTRA_LINE_ID);
        lineName = intent.getStringExtra(EXTRA_LINE_NAME);
        stationName = intent.getStringExtra(EXTRA_STATION_NAME);
        cityId = intent.getStringExtra(EXTRA_CITY_ID);
        direction = intent.getIntExtra(EXTRA_DIRECTION, 0);
        stationOrder = intent.getIntExtra(EXTRA_STATION_ORDER, 0);
        advance = intent.getIntExtra(EXTRA_ADVANCE, 1);
        kind = intent.getStringExtra(EXTRA_KIND);
        if (kind == null || kind.isEmpty()) kind = KIND_BOARD;
        if (lineId == null || lineId.isEmpty() || stationOrder <= 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startOngoing();
        startLoop();
        return START_STICKY;
    }

    private void startOngoing() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ReminderService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text;
        if (KIND_ALIGHT.equals(kind)) {
            text = "准备在「" + stationName + "」下车 · 提前 " + advance + " 站提醒";
        } else if (advance <= 0) {
            text = "车辆到站时提醒你 · 「" + stationName + "」";
        } else {
            text = "到「" + stationName + "」前 " + advance + " 站提醒";
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ONGOING)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_bus)
                .setContentTitle("正在监控 " + lineName)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "停止提醒", stopPi).build());
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ONGOING_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ONGOING_ID, b.build());
        }
    }

    private void startLoop() {
        running = true;
        if (worker != null) worker.interrupt();
        worker = new Thread(() -> {
            int failures = 0;
            while (running) {
                try {
                    App app = App.get();
                    Api api = new Api(app.prefs.getString("userId", "browser_reminder"), cityId, app.lat(), app.lng());
                    Models.Detail detail = api.lineDetail(lineId, lineName == null ? "" : lineName, direction, "", "", "", stationOrder, cityId);
                    failures = 0;
                    if (detail != null && checkAndAlert(detail)) {
                        return;
                    }
                } catch (Exception e) {
                    failures++;
                }
                try {
                    Thread.sleep(failures > 3 ? 60000L : 20000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "reminder-loop");
        worker.start();
    }

    private boolean checkAndAlert(Models.Detail detail) {
        for (Models.Bus bus : detail.buses) {
            if (bus.order <= 0 || bus.order > stationOrder) continue;
            int left = stationOrder - bus.order;
            if (left <= advance) {
                boolean alight = KIND_ALIGHT.equals(kind);
                String text;
                if (left <= 0) {
                    text = alight
                            ? "即将到达「" + stationName + "」，请准备下车"
                            : "车辆即将到站「" + stationName + "」，请准备上车";
                } else {
                    text = "还有 " + left + " 站到「" + stationName + "」"
                            + (alight ? "，请准备下车" : "")
                            + (bus.distanceToSc > 0 ? String.format(Locale.US, "，约 %.0f 米", bus.distanceToSc) : "");
                }
                alert(text);
                return true;
            }
        }
        return false;
    }

    private void alert(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ALERT)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_bus)
                .setContentTitle(lineName + (KIND_ALIGHT.equals(kind) ? " 下车提醒" : " 到站提醒"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ALERT_ID, b.build());
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
