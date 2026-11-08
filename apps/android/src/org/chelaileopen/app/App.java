package org.chelaileopen.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.chelaileopen.app.core.Api;
import org.chelaileopen.app.core.Models;
import org.chelaileopen.app.data.HistoryStore;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 全局状态：设置、缓存、收藏、线程池。 */
public class App extends Application {
    public interface Done<T> {
        void ok(T value);
    }

    public interface Fail {
        void error(Exception e);
    }

    private static App instance;

    public SharedPreferences prefs;
    public Api api;
    public Handler main;
    public ExecutorService io;
    public HistoryStore history;

    private final Map<String, List<Models.Line>> lineCache = new HashMap<>();
    private List<Models.City> cityCache;

    public static App get(Context ctx) {
        return (App) ctx.getApplicationContext();
    }

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("chelaile", MODE_PRIVATE);
        main = new Handler(Looper.getMainLooper());
        io = Executors.newFixedThreadPool(3);
        history = new HistoryStore(this);

        String userId = prefs.getString("userId", null);
        if (userId == null) {
            String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
            StringBuilder sb = new StringBuilder("browser_");
            Random r = new Random();
            for (int i = 0; i < 8; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            userId = sb.toString();
            prefs.edit().putString("userId", userId).apply();
        }
        api = new Api(userId, cityId(), lat(), lng());
    }

    public String cityId() {
        return prefs.getString("cityId", "241");
    }

    public String cityName() {
        return prefs.getString("cityName", "韶关");
    }

    public double lat() {
        return Double.parseDouble(prefs.getString("lat", "24.813761"));
    }

    public double lng() {
        return Double.parseDouble(prefs.getString("lng", "113.591965"));
    }

    public int refreshSeconds() {
        return prefs.getInt("refreshSec", 15);
    }

    public boolean lightMode() {
        return prefs.getBoolean("lightMode", false);
    }

    public void setLightMode(boolean light) {
        prefs.edit().putBoolean("lightMode", light).apply();
    }

    /** 字号倍数：标准 1.0、大 1.15、特大 1.3、超大 1.5。 */
    public float fontScale() {
        return prefs.getFloat("fontScale", 1f);
    }

    public void setFontScale(float scale) {
        if (scale < 0.8f) scale = 0.8f;
        if (scale > 1.8f) scale = 1.8f;
        prefs.edit().putFloat("fontScale", scale).apply();
    }

    public void setCity(String cityId, String cityName) {
        prefs.edit().putString("cityId", cityId).putString("cityName", cityName).apply();
        api.setCity(cityId);
        lineCache.clear();
    }

    public void setGeo(double lat, double lng) {
        prefs.edit()
                .putString("lat", String.valueOf(lat))
                .putString("lng", String.valueOf(lng))
                .putBoolean("geoSet", true)
                .apply();
        api.setGeo(lat, lng);
    }

    /** 用户是否已经有过一次成功定位（包括手动定位）。 */
    public boolean hasGeo() {
        return prefs.getBoolean("geoSet", false);
    }

    /** 自动定位只尝试一次，避免每次启动都弹系统权限框。 */
    public boolean autoLocateTried() {
        return prefs.getBoolean("autoLocateTried", false);
    }

    public void markAutoLocateTried() {
        prefs.edit().putBoolean("autoLocateTried", true).apply();
    }

    public void setRefreshSeconds(int sec) {
        prefs.edit().putInt("refreshSec", sec).apply();
    }

    /** 统一的后台任务入口：结果回主线程。 */
    public <T> void run(Callable<T> task, Done<T> done, Fail fail) {
        io.execute(() -> {
            try {
                T value = task.call();
                if (done != null) main.post(() -> done.ok(value));
            } catch (Exception e) {
                if (fail != null) main.post(() -> fail.error(e));
            }
        });
    }

    public List<Models.City> cachedCities() {
        return cityCache;
    }

    public void cacheCities(List<Models.City> cities) {
        cityCache = cities;
    }

    private File linesFile(String cityId) {
        return new File(getCacheDir(), "lines_" + cityId + ".json");
    }

    /** 城市线路表：内存 → 24h 文件缓存 → 网络。 */
    public List<Models.Line> linesForCity(String cityId) throws Exception {
        List<Models.Line> mem = lineCache.get(cityId);
        if (mem != null) return mem;
        File f = linesFile(cityId);
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 24L * 3600 * 1000) {
            try {
                String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                JSONArray arr = new JSONArray(text);
                List<Models.Line> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) list.add(Models.Line.from(arr.optJSONObject(i)));
                if (!list.isEmpty()) {
                    lineCache.put(cityId, list);
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        List<Models.Line> list = api.cityLines(cityId);
        JSONArray arr = new JSONArray();
        try {
            for (Models.Line l : list) {
                JSONObject o = new JSONObject();
                o.put("lineId", l.lineId);
                o.put("name", l.name);
                o.put("startSn", l.startSn);
                o.put("endSn", l.endSn);
                o.put("firstTime", l.firstTime);
                o.put("lastTime", l.lastTime);
                o.put("direction", l.direction);
                arr.put(o);
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        lineCache.put(cityId, list);
        return list;
    }

    public List<Models.Line> searchLines(String keyword, String cityId) throws Exception {
        List<Models.Line> all = linesForCity(cityId);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Models.Line> exact = new ArrayList<>();
        List<Models.Line> fuzzy = new ArrayList<>();
        if (kw.isEmpty()) return fuzzy;
        for (Models.Line l : all) {
            String name = l.name.toLowerCase(Locale.ROOT);
            if (name.equals(kw)) exact.add(l);
            else if (name.contains(kw)) fuzzy.add(l);
        }
        if (!exact.isEmpty()) return exact;
        fuzzy.sort((a, b) -> Integer.compare(a.name.length(), b.name.length()));
        return fuzzy.size() > 40 ? new ArrayList<>(fuzzy.subList(0, 40)) : fuzzy;
    }

    // ---------- 收藏 ----------

    public JSONArray favorites() {
        try {
            return new JSONArray(prefs.getString("favorites", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public boolean isFavorite(String lineId) {
        JSONArray arr = favorites();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && lineId.equals(o.optString("lineId"))) return true;
        }
        return false;
    }

    public void toggleFavorite(Models.Line line, String cityId) {
        JSONArray arr = favorites();
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (line.lineId.equals(o.optString("lineId"))) {
                removed = true;
                continue;
            }
            next.put(o);
        }
        if (!removed) {
            try {
                JSONObject o = new JSONObject();
                o.put("lineId", line.lineId);
                o.put("name", line.name);
                o.put("direction", line.direction);
                o.put("startSn", line.startSn);
                o.put("endSn", line.endSn);
                o.put("cityId", cityId);
                next.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString("favorites", next.toString()).apply();
    }

    public void removeFavorite(String lineId, int direction) {
        JSONArray arr = favorites();
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (lineId.equals(o.optString("lineId")) && o.optInt("direction", 0) == direction) continue;
            next.put(o);
        }
        prefs.edit().putString("favorites", next.toString()).apply();
    }
}
