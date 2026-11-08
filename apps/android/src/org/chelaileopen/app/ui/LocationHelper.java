package org.chelaileopen.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.chelaileopen.app.MainActivity;

/** 一次性定位：优先缓存，其次单次请求。 */
public final class LocationHelper {
    public interface Callback {
        void onLocation(double lat, double lng);

        void onFail(String message);
    }

    private static Callback pending;
    private static java.lang.ref.WeakReference<MainActivity> current;

    private LocationHelper() {
    }

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void attach(MainActivity activity) {
        current = new java.lang.ref.WeakReference<>(activity);
    }

    public static void request(MainActivity activity, Callback cb) {
        attach(activity);
        if (!hasPermission(activity)) {
            pending = cb;
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 12);
            return;
        }
        locate(activity, cb);
    }

    public static void onPermissionResult(MainActivity activity, int requestCode, int[] grantResults) {
        if (requestCode != 12) return;
        Callback cb = pending;
        pending = null;
        if (cb == null) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            locate(activity, cb);
        } else {
            cb.onFail("没有定位权限，可在系统设置里开启");
        }
    }

    private static void locate(Activity activity, Callback cb) {
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            cb.onFail("设备不支持定位");
            return;
        }

        // 读取缓存用于超时兜底；超过 30 秒的缓存不直接采信，继续等待一次新鲜定位。
        Location best = null;
        long now = System.currentTimeMillis();
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.isProviderEnabled(provider)) continue;
                Location l = lm.getLastKnownLocation(provider);
                if (l == null || now - l.getTime() > 5 * 60 * 1000L) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            }
        } catch (SecurityException ignored) {
        }
        // 缓存超过 30 秒就继续等一次新鲜定位；模拟器和真机都可能有旧城市缓存。
        if (best != null && now - best.getTime() <= 30 * 1000L) {
            cb.onLocation(best.getLatitude(), best.getLongitude());
            return;
        }
        final Location cached = best;

        final boolean[] done = {false};
        final LocationListener[] listenerRef = new LocationListener[1];
        final android.os.Handler main = new android.os.Handler(Looper.getMainLooper());
        final Runnable[] timeoutRef = new Runnable[1];

        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                if (timeoutRef[0] != null) main.removeCallbacks(timeoutRef[0]);
                if (listenerRef[0] != null) {
                    try {
                        lm.removeUpdates(listenerRef[0]);
                    } catch (Exception ignored) {
                    }
                }
                if (cached != null) {
                    cb.onLocation(cached.getLatitude(), cached.getLongitude());
                } else {
                    cb.onFail("定位超时，请到室外或打开系统定位后重试");
                }
            }
        };

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (done[0] || location == null) return;
                done[0] = true;
                if (timeoutRef[0] != null) main.removeCallbacks(timeoutRef[0]);
                try {
                    lm.removeUpdates(this);
                } catch (Exception ignored) {
                }
                cb.onLocation(location.getLatitude(), location.getLongitude());
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        listenerRef[0] = listener;

        int started = 0;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.isProviderEnabled(provider)) continue;
                lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
                started++;
            }
        } catch (Exception e) {
            if (!done[0]) {
                done[0] = true;
                cb.onFail("定位失败：" + e.getMessage());
            }
            return;
        }
        if (started == 0) {
            done[0] = true;
            cb.onFail("系统定位已关闭，请打开 GPS 或网络定位");
            return;
        }

        timeoutRef[0] = finish;
        main.postDelayed(finish, 12000L);
    }
}

