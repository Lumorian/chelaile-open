package org.chelaileopen.app.core;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 车来了 H5 网关的最小客户端。对应 packages/protocol/src/client.ts。 */
public class Api {
    public static final String BASE = "https://web.chelaile.net.cn";
    private static final String AES_KEY = "422556651C7F7B2B5C266EED06068230";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; PKM110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36";
    private static final String SIGN_SALT = "qwihrnbtmj";

    public static class ApiException extends Exception {
        public final String status;
        public final String endpoint;

        public ApiException(String status, String endpoint, String message) {
            super(message);
            this.status = status;
            this.endpoint = endpoint;
        }
    }

    private final String userId;
    private String cityId;
    private double lat;
    private double lng;

    public Api(String userId, String cityId, double lat, double lng) {
        this.userId = userId;
        this.cityId = cityId;
        this.lat = lat;
        this.lng = lng;
    }

    public void setCity(String cityId) {
        this.cityId = cityId;
    }

    public void setGeo(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public String getCityId() {
        return cityId;
    }

    private String encode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String query(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private Map<String, String> baseParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("s", "h5");
        p.put("v", "9.1.2");
        p.put("vc", "1");
        p.put("src", "wechat_shaoguan");
        p.put("sign", "1");
        p.put("userId", userId);
        p.put("h5Id", userId);
        p.put("cityId", cityId);
        p.put("geo_lat", String.valueOf(lat));
        p.put("geo_lng", String.valueOf(lng));
        p.put("lat", String.valueOf(lat));
        p.put("lng", String.valueOf(lng));
        p.put("gpstype", "wgs");
        return p;
    }

    /**
     * 官方 App 的公共参数。实测只有用 App 参数请求 stop!nearlines.action 和
     * line!timeTable.action 才会返回明文动态车辆；H5 参数在非韶关会拿到空 buses。
     */
    private Map<String, String> appParams(String cid) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("s", "android");
        p.put("v", "7.6.8");
        p.put("vc", "436");
        p.put("src", "android");
        p.put("sign", "1");
        p.put("userId", userId);
        p.put("h5Id", userId);
        p.put("cityId", cid == null || cid.isEmpty() ? cityId : cid);
        p.put("geo_lat", String.valueOf(lat));
        p.put("geo_lng", String.valueOf(lng));
        p.put("lat", String.valueOf(lat));
        p.put("lng", String.valueOf(lng));
        p.put("gpstype", "wgs");
        return p;
    }

    private String cryptoSign(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"lineId", "lineName", "direction", "stationName", "nextStationName", "lineNo", "targetOrder"}) {
            String v = params.get(key);
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(key).append('=').append(v);
        }
        return Crypto.md5Hex(sb.toString() + SIGN_SALT);
    }

    private JSONObject getJson(String path, Map<String, String> params, boolean signed) throws Exception {
        if (signed) {
            params.put("cryptoSign", cryptoSign(params));
        }
        String url = BASE + path + "?" + query(params);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", BASE + "/");
            conn.setRequestProperty("Connection", "close");
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
                reader.close();
            }
            if (code != 200) {
                throw new ApiException("http" + code, path, "HTTP " + code);
            }
            String text = sb.toString().trim();
            if (text.startsWith("**YGKJ")) {
                text = text.substring(6);
                if (text.endsWith("YGKJ##")) text = text.substring(0, text.length() - 6);
            }
            JSONObject root = new JSONObject(text);
            JSONObject jsonr = root.optJSONObject("jsonr");
            if (jsonr == null) {
                // 城市列表等少数接口直接返回 {"data": ...}，没有 jsonr 包裹。
                JSONObject direct = root.optJSONObject("data");
                if (direct != null) return direct;
                throw new ApiException("parse", path, "响应缺少 jsonr");
            }
            String status = jsonr.optString("status", "");
            if ("01".equals(status)) return null; // 无数据，不是错误
            if (!"00".equals(status)) {
                String msg = jsonr.isNull("errmsg") ? "" : jsonr.optString("errmsg", "");
                throw new ApiException(status, path, msg.isEmpty() ? ("接口返回状态 " + status) : msg);
            }
            Object dataObj = jsonr.opt("data");
            if (!(dataObj instanceof JSONObject)) return null;
            JSONObject data = (JSONObject) dataObj;
            if (data.has("encryptResult")) {
                String b64 = data.optString("encryptResult", "");
                byte[] plain = Crypto.aes256EcbDecrypt(AES_KEY.getBytes(StandardCharsets.UTF_8), Base64.decode(b64, Base64.DEFAULT));
                return new JSONObject(new String(plain, StandardCharsets.UTF_8));
            }
            return data;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 城市列表（走老 App 的 cdatasource 接口，不带 H5 公共参数）。 */
    public List<Models.City> cities() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("type", "all");
        p.put("s", "android");
        p.put("v", "3.80.0");
        p.put("src", "webapp_default");
        JSONObject data = getJson("/cdatasource/citylist", p, false);
        List<Models.City> out = new ArrayList<>();
        if (data != null) {
            JSONArray arr = data.optJSONArray("allRealtimeCity");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) out.add(Models.City.from(arr.optJSONObject(i)));
            }
        }
        return out;
    }

    /** 根据坐标反查城市名。 */
    public String locateCity(double lat, double lng) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("type", "gpsRealtimeCity");
        p.put("lat", String.valueOf(lat));
        p.put("lng", String.valueOf(lng));
        p.put("gpstype", "wgs");
        p.put("s", "android");
        p.put("v", "3.80.0");
        p.put("src", "webapp_default");
        JSONObject data = getJson("/cdatasource/citylist", p, false);
        if (data == null) return null;
        JSONObject city = data.optJSONObject("gpsRealtimeCity");
        return city == null ? null : Models.str(city, "cityName");
    }

    public List<Models.Line> cityLines(String cityId) throws Exception {
        Map<String, String> p = appParams(cityId);
        JSONObject data = getJson("/api/bus/cityLineList", p, false);
        List<Models.Line> out = new ArrayList<>();
        if (data != null) {
            JSONObject all = data.optJSONObject("allLines");
            JSONArray arr = all == null ? null : all.optJSONArray("all");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) out.add(Models.Line.from(arr.optJSONObject(i)));
            }
        }
        return out;
    }

    public Models.Route lineRoute(String lineId, String cityId) throws Exception {
        Map<String, String> p = appParams(cityId);
        p.put("lineId", lineId);
        return Models.Route.from(getJson("/api/bus/line!lineRoute.action", p, false));
    }

    public Models.NearLines nearLines(double lat, double lng, String cityId) throws Exception {
        Map<String, String> p = appParams(cityId);
        p.put("lat", String.valueOf(lat));
        p.put("lng", String.valueOf(lng));
        p.put("cityState", "2");
        // App 参数 + 非 encrypted 端点会直接返回明文动态 stnStates，非韶关也有实时。
        return Models.NearLines.from(getJson("/api/bus/stop!nearlines.action", p, false));
    }

    public Models.NearStations nearStations(double lat, double lng, String cityId) throws Exception {
        Map<String, String> p = appParams(cityId);
        p.put("lat", String.valueOf(lat));
        p.put("lng", String.valueOf(lng));
        p.put("cityState", "2");
        return Models.NearStations.from(getJson("/api/bus/stop!nearPhysicalStns.action", p, false));
    }

    public Models.Detail lineDetail(String lineId, String lineName, int direction, String stationName,
                                    String nextStationName, String lineNo, int targetOrder, String cityId) throws Exception {
        Map<String, String> p = baseParams();
        p.put("cityId", cityId);
        p.put("lineId", lineId);
        p.put("lineName", lineName);
        p.put("direction", String.valueOf(direction));
        p.put("stationName", stationName);
        p.put("nextStationName", nextStationName);
        p.put("lineNo", lineNo == null ? "" : lineNo);
        p.put("targetOrder", String.valueOf(targetOrder));
        Models.Detail detail = Models.Detail.from(getJson("/api/bus/line!encryptedLineDetail.action", p, true));
        if (detail == null) return null;

        // H5 线路详情在非韶关只返回静态站点（buses=[]）。App 的 timeTable 接口
        // 仍会返回动态车辆，这里把它补进同一个 Detail，UI 不用区分城市。
        if (detail.buses.isEmpty()) {
            try {
                Models.DynamicLine dyn = lineTimeTable(lineId, lineName, direction, targetOrder, cityId);
                if (dyn != null) {
                    if (!dyn.buses.isEmpty()) {
                        detail.buses = dyn.buses;
                        detail.realData = true;
                        if (detail.targetOrder <= 0 && dyn.targetOrder > 0) detail.targetOrder = dyn.targetOrder;
                        if (detail.predictionText.contains("暂无车辆")) detail.predictionText = "";
                        if (detail.depDesc.contains("暂无车辆")) detail.depDesc = "";
                    }
                    if (detail.line != null && dyn.line != null) {
                        if (dyn.line.state != 0 || detail.line.state == -5) detail.line.state = dyn.line.state;
                        if (!dyn.line.desc.isEmpty()
                                && (detail.line.desc.isEmpty() || detail.line.desc.contains("暂无车辆"))) {
                            detail.line.desc = dyn.line.desc;
                        }
                        if (detail.depDesc.isEmpty() && !dyn.line.desc.isEmpty()) {
                            detail.depDesc = dyn.line.desc;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 动态补全失败不影响静态线路详情。
            }
        }
        return detail;
    }

    /** 官方 App 的实时车辆接口（明文）。不含完整站点表，只用于补全 lineDetail。 */
    public Models.DynamicLine lineTimeTable(String lineId, String lineName, int direction, int targetOrder,
                                            String cityId) throws Exception {
        Map<String, String> p = appParams(cityId);
        p.put("lineId", lineId);
        p.put("lineName", lineName);
        p.put("direction", String.valueOf(direction));
        p.put("targetOrder", String.valueOf(targetOrder));
        return Models.DynamicLine.from(getJson("/api/bus/line!timeTable.action", p, false));
    }

    public Models.StationDetail stationDetail(String physicalStId, String stnName, String cityId) throws Exception {
        Map<String, String> p = baseParams();
        p.put("cityId", cityId);
        p.put("physicalStId", physicalStId);
        p.put("stnName", stnName);
        return Models.StationDetail.from(getJson("/api/bus/stop!encryptedPhyStnDetail.action", p, false));
    }

    /** 官方地名搜索：POI + 公交站 + 相关线路。参数名必须是 key。 */
    public Models.PlaceSearch searchPlaces(String key) throws Exception {
        Map<String, String> p = baseParams();
        p.put("key", key == null ? "" : key.trim());
        return Models.PlaceSearch.from(getJson("/api/bus/query!encryptedNSearch.action", p, false));
    }

    /** 首站发车时刻表（发车时间功能）。没有数据时返回空对象。 */
    public Models.Timetable depTimeTable(String lineId, String lineName, int direction, int targetOrder) throws Exception {
        Map<String, String> p = baseParams();
        p.put("lineId", lineId);
        p.put("lineName", lineName);
        p.put("direction", String.valueOf(direction));
        p.put("targetOrder", String.valueOf(targetOrder));
        return Models.Timetable.from(getJson("/api/bus/line!depTimeTable.action", p, false));
    }

    /** 线路简介（票价/运营时间/里程），失败时返回空字符串。 */
    public String lineSummary(String lineId, String lineName, int direction) {
        try {
            Map<String, String> p = baseParams();
            p.put("lineId", lineId);
            p.put("lineName", lineName);
            p.put("direction", String.valueOf(direction));
            JSONObject data = getJson("/api/bus/line!summary.action", p, false);
            return data == null ? "" : Models.str(data, "lineSummary");
        } catch (Exception e) {
            return "";
        }
    }
}
