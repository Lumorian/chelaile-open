package org.chelaileopen.app.core;

/** WGS84 / GCJ-02 / BD-09 互转，以及球面距离。上游站点坐标可能是 bd09，需要转换后再算距离。 */
public final class Geo {
    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private Geo() {
    }

    public static boolean outOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (outOfChina(lat, lng)) return new double[]{lat, lng};
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    public static double[] gcj02ToWgs84(double lat, double lng) {
        double wgLat = lat;
        double wgLng = lng;
        for (int i = 0; i < 4; i++) {
            double[] gcj = wgs84ToGcj02(wgLat, wgLng);
            wgLat += lat - gcj[0];
            wgLng += lng - gcj[1];
        }
        return new double[]{wgLat, wgLng};
    }

    public static double[] bd09ToGcj02(double lat, double lng) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * PI * 3000.0 / 180.0);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    public static double[] gcj02ToBd09(double lat, double lng) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * PI * 3000.0 / 180.0);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * PI * 3000.0 / 180.0);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }

    /** BD-09 → WGS84。 */
    public static double[] bd09ToWgs84(double lat, double lng) {
        double[] gcj = bd09ToGcj02(lat, lng);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    /**
     * 上游站点的“用于展示/排序”的坐标统一转成 WGS84。
     * lineDetail 的 lat/lng 是 BD-09，wgsLat/wgsLng 才是 WGS-84；lineRoute 只有 BD-09。
     */
    public static double[] stationToWgs(Models.Stop s) {
        if (s.wgsLat != 0 && s.wgsLng != 0) return new double[]{s.wgsLat, s.wgsLng};
        if (s.lat == 0 && s.lng == 0) return new double[]{0, 0};
        return bd09ToWgs84(s.lat, s.lng);
    }

    /** 球面距离（米）。 */
    public static int distance(double lat1, double lng1, double lat2, double lng2) {
        if (lat1 == 0 || lat2 == 0) return -1;
        double radLat1 = lat1 * PI / 180.0;
        double radLat2 = lat2 * PI / 180.0;
        double a = radLat1 - radLat2;
        double b = (lng1 - lng2) * PI / 180.0;
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137.0;
        return (int) Math.round(s);
    }

    public static String formatDistance(int meters) {
        if (meters < 0) return "";
        if (meters < 1000) return meters + " 米";
        return String.format(java.util.Locale.US, "%.1f 公里", meters / 1000.0);
    }
}
