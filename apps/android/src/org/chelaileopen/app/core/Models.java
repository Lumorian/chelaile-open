package org.chelaileopen.app.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 服务端 JSON 的轻量映射。所有字段都按“缺失即默认值”解析，接口改字段时不至于崩溃。 */
public final class Models {
    private Models() {
    }

    public static String str(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
    }

    public static int i(JSONObject o, String key, int def) {
        return o == null ? def : o.optInt(key, def);
    }

    public static double d(JSONObject o, String key, double def) {
        return o == null ? def : o.optDouble(key, def);
    }

    public static boolean b(JSONObject o, String key, boolean def) {
        return o == null ? def : o.optBoolean(key, def);
    }

    /** 取第一个非空字符串（接口不同入口字段名不一致）。 */
    public static String first(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
    }

    public static final class City {
        public String cityId = "";
        public String cityName = "";
        public String pinyin = "";
        public boolean hot;

        public static City from(JSONObject o) {
            City c = new City();
            c.cityId = str(o, "cityId");
            c.cityName = str(o, "cityName");
            c.pinyin = str(o, "pinyin");
            c.hot = i(o, "hot", 0) == 1;
            return c;
        }
    }

    public static class Line {
        public String lineId = "";
        public String name = "";
        public String lineNo = "";
        public String startSn = "";
        public String endSn = "";
        public String firstTime = "";
        public String lastTime = "";
        public String price = "";
        public String desc = "";
        public String shortDesc = "";
        public int direction;
        public int state = -1;
        public int stationsNum;
        public List<String[]> segTimes = new ArrayList<>();

        public static Line from(JSONObject o) {
            Line l = new Line();
            if (o == null) return l;
            l.lineId = str(o, "lineId");
            l.name = first(str(o, "name"), str(o, "lineName"));
            l.lineNo = first(str(o, "lineNo"), l.name);
            l.startSn = first(str(o, "startSn"), str(o, "startStopName"));
            l.endSn = first(str(o, "endSn"), str(o, "endStopName"));
            l.firstTime = str(o, "firstTime");
            l.lastTime = str(o, "lastTime");
            l.price = str(o, "price");
            l.desc = str(o, "desc");
            l.shortDesc = str(o, "shortDesc");
            l.direction = i(o, "direction", 0);
            l.state = i(o, "state", -1);
            l.stationsNum = i(o, "stationsNum", 0);
            JSONArray seg = o.optJSONArray("segTimes");
            if (seg != null) {
                for (int k = 0; k < seg.length(); k++) {
                    JSONArray pair = seg.optJSONArray(k);
                    if (pair != null && pair.length() >= 2) {
                        l.segTimes.add(new String[]{pair.optString(0), pair.optString(1)});
                    }
                }
            }
            return l;
        }
    }

    public static final class Stop {
        public int order;
        public String sId = "";
        public String sn = "";
        public String physicalStId = "";
        public String namesakeStId = "";
        public double lat;
        public double lng;
        public double wgsLat;
        public double wgsLng;
        public int distanceToSp;
        public int specialOrder;
        public String gpsType = "";

        public static Stop from(JSONObject o) {
            Stop s = new Stop();
            if (o == null) return s;
            s.order = i(o, "order", 0);
            s.sId = str(o, "sId");
            s.sn = str(o, "sn");
            s.physicalStId = str(o, "physicalStId");
            s.namesakeStId = str(o, "namesakeStId");
            s.lat = d(o, "lat", 0);
            s.lng = d(o, "lng", 0);
            s.wgsLat = d(o, "wgsLat", 0);
            s.wgsLng = d(o, "wgsLng", 0);
            s.distanceToSp = i(o, "distanceToSp", 0);
            s.specialOrder = i(o, "specialOrder", s.order);
            s.gpsType = str(o, "gpsType");
            if (s.gpsType.isEmpty()) s.gpsType = str(o, "gpstype");
            return s;
        }

        /** 优先用 wgs 坐标；没有则把接口的 bd09 坐标转换成 wgs84。 */
        public double bestLat() {
            return Geo.stationToWgs(this)[0];
        }

        public double bestLng() {
            return Geo.stationToWgs(this)[1];
        }
    }

    public static final class Bus {
        public String busId = "";
        public int order;
        public double lat;
        public double lng;
        public double distanceToSc;
        public double distanceToWaitStn;
        public int speed;
        public String timeStr = "";
        public String busDesc = "";
        public String assistDesc = "";
        public String nextStationName = "";
        public int state;
        /** App 的 timeTable 接口会返回每辆车到目标站的预测（travels）。 */
        public List<BusTravel> travels = new ArrayList<>();

        public static Bus from(JSONObject o) {
            Bus b = new Bus();
            if (o == null) return b;
            b.busId = str(o, "busId");
            b.order = i(o, "order", 0);
            b.lat = d(o, "lat", 0);
            b.lng = d(o, "lng", 0);
            b.distanceToSc = d(o, "distanceToSc", -1);
            b.distanceToWaitStn = d(o, "distanceToWaitStn", -1);
            b.speed = i(o, "speed", 0);
            b.timeStr = str(o, "timeStr");
            b.busDesc = str(o, "busDesc");
            b.assistDesc = str(o, "assistDesc");
            b.nextStationName = str(o, "nextStationName");
            b.state = i(o, "state", 0);
            JSONArray tr = o.optJSONArray("travels");
            if (tr != null) {
                for (int k = 0; k < tr.length(); k++) b.travels.add(BusTravel.from(tr.optJSONObject(k)));
            }
            return b;
        }

        public BusTravel travelFor(int targetOrder) {
            for (BusTravel t : travels) if (t.order == targetOrder) return t;
            return null;
        }
    }

    public static final class BusTravel {
        public int order;
        public long arrivalTime;
        public int travelTime = -1;
        public String recommTip = "";

        public static BusTravel from(JSONObject o) {
            BusTravel t = new BusTravel();
            if (o == null) return t;
            t.order = i(o, "order", 0);
            t.arrivalTime = o.optLong("arrivalTime", 0L);
            t.travelTime = i(o, "travelTime", -1);
            t.recommTip = str(o, "recommTip");
            return t;
        }
    }

    public static final class Detail {
        public Line line = new Line();
        public List<Stop> stops = new ArrayList<>();
        public List<Bus> buses = new ArrayList<>();
        public List<Line> otherLines = new ArrayList<>();
        public int targetOrder;
        public int nearStnOrder;
        public int depIntervalM = -1;
        public int intervalLeft = -1;
        public int intervalRight = -1;
        public int depTable;
        public int menuDepTable;
        public int histimeRefreshType;
        public boolean realData;
        public String depDesc = "";
        public String preArrivalTime = "";
        public String predictionText = "";
        public String predictionLink = "";
        public String tip = "";
        public String toast = "";
        public int refreshInterval;

        public static Detail from(JSONObject o) {
            Detail det = new Detail();
            if (o == null) return det;
            det.line = Line.from(o.optJSONObject("line"));
            JSONArray st = o.optJSONArray("stations");
            if (st != null) {
                for (int k = 0; k < st.length(); k++) det.stops.add(Stop.from(st.optJSONObject(k)));
            }
            JSONArray bs = o.optJSONArray("buses");
            if (bs != null) {
                for (int k = 0; k < bs.length(); k++) det.buses.add(Bus.from(bs.optJSONObject(k)));
            }
            JSONArray ol = o.optJSONArray("otherlines");
            if (ol != null) {
                for (int k = 0; k < ol.length(); k++) det.otherLines.add(Line.from(ol.optJSONObject(k)));
            }
            det.targetOrder = i(o, "targetOrder", 0);
            det.nearStnOrder = i(o, "nearStnOrder", 0);
            det.depIntervalM = i(o, "depIntervalM", -1);
            det.intervalLeft = i(o, "intervalLeft", -1);
            det.intervalRight = i(o, "intervalRight", -1);
            det.depTable = i(o, "depTable", 0);
            det.menuDepTable = i(o, "menuDepTable", 0);
            det.histimeRefreshType = i(o, "histimeRefreshType", 0);
            det.realData = b(o, "realData", false);
            det.depDesc = str(o, "depDesc");
            det.preArrivalTime = str(o, "preArrivalTime");
            det.predictionText = str(o, "predictionText");
            det.predictionLink = str(o, "predictionLink");
            det.refreshInterval = i(o, "refreshInterval", 0);
            JSONObject tipObj = o.optJSONObject("tip");
            det.tip = tipObj != null ? str(tipObj, "desc") : str(o, "tip");
            det.toast = str(o, "toast");
            return det;
        }

        public Stop findStop(int order) {
            for (Stop s : stops) if (s.order == order) return s;
            return null;
        }
    }

    /** App timeTable 接口返回的实时车辆包（不含完整站点表）。 */
    public static final class DynamicLine {
        public Line line = new Line();
        public List<Bus> buses = new ArrayList<>();
        public int targetOrder;
        public String depTimeLink = "";
        public String depTimeName = "";
        public String depTimeTitle = "";

        public static DynamicLine from(JSONObject o) {
            DynamicLine d = new DynamicLine();
            if (o == null) return d;
            d.line = Line.from(o.optJSONObject("line"));
            d.targetOrder = i(o, "targetOrder", 0);
            JSONArray bs = o.optJSONArray("buses");
            if (bs != null) {
                for (int k = 0; k < bs.length(); k++) d.buses.add(Bus.from(bs.optJSONObject(k)));
            }
            JSONObject e = o.optJSONObject("depTimeEntrance");
            if (e != null) {
                d.depTimeLink = str(e, "link");
                d.depTimeName = str(e, "name");
                d.depTimeTitle = str(e, "title");
            }
            return d;
        }
    }

    public static final class Route {
        public Line line = new Line();
        public List<Stop> stations = new ArrayList<>();
        public List<double[]> path = new ArrayList<>();
        public String gpsType = "";

        public static Route from(JSONObject o) {
            Route r = new Route();
            if (o == null) return r;
            r.gpsType = o.optString("gpstype", "");
            r.line = Line.from(o.optJSONObject("line"));
            JSONArray st = o.optJSONArray("stations");
            if (st != null) {
                for (int k = 0; k < st.length(); k++) {
                    Stop s = Stop.from(st.optJSONObject(k));
                    if (s.gpsType.isEmpty()) s.gpsType = r.gpsType;
                    r.stations.add(s);
                }
            }
            JSONArray rt = o.optJSONArray("route");
            if (rt != null) {
                for (int k = 0; k < rt.length(); k++) {
                    JSONObject p = rt.optJSONObject(k);
                    if (p != null) r.path.add(new double[]{d(p, "lat", 0), d(p, "lng", 0)});
                }
            }
            return r;
        }
    }

    public static final class NearLine {
        public Line line = new Line();
        public Stop targetStation = new Stop();
        public List<StnState> stnStates = new ArrayList<>();
        public int depIntervalM = -1;
        public int intervalLeft = -1;
        public int intervalRight = -1;
        public int traffic;

        public static NearLine from(JSONObject o) {
            NearLine n = new NearLine();
            if (o == null) return n;
            n.line = Line.from(o.optJSONObject("line"));
            n.targetStation = Stop.from(o.optJSONObject("targetStation"));
            n.depIntervalM = i(o, "depIntervalM", -1);
            n.intervalLeft = i(o, "intervalLeft", -1);
            n.intervalRight = i(o, "intervalRight", -1);
            n.traffic = i(o, "traffic", 0);
            JSONArray ss = o.optJSONArray("stnStates");
            if (ss != null) {
                for (int k = 0; k < ss.length(); k++) n.stnStates.add(StnState.from(ss.optJSONObject(k)));
            }
            return n;
        }
    }

    public static final class StnState {
        public int order;
        public String sn = "";
        public int time = -1;
        public int seconds = -1;
        public long arrivalTime;
        public int stops = -1;
        public String busId = "";
        public int distance = -1;
        public List<Bus> buses = new ArrayList<>();

        public static StnState from(JSONObject o) {
            StnState s = new StnState();
            if (o == null) return s;
            s.order = i(o, "order", 0);
            s.sn = str(o, "sn");
            s.time = i(o, "time", -1);
            s.seconds = i(o, "seconds", i(o, "travelTime", -1));
            s.arrivalTime = o.optLong("arrivalTime", 0L);
            s.stops = i(o, "value", -1);
            s.busId = str(o, "busId");
            s.distance = i(o, "distance", -1);
            JSONArray bs = o.optJSONArray("buses");
            if (bs != null) {
                for (int k = 0; k < bs.length(); k++) s.buses.add(Bus.from(bs.optJSONObject(k)));
            }
            return s;
        }
    }

    public static final class NearGroup {
        public int distance;
        public String sn = "";
        public String sId = "";
        public int sType;
        public double lat;
        public double lng;
        public String tag = "";
        public Stop targetStation = new Stop();
        public List<NearLine> lines = new ArrayList<>();

        public static NearGroup from(JSONObject o) {
            NearGroup g = new NearGroup();
            if (o == null) return g;
            g.distance = i(o, "distance", 0);
            g.sn = str(o, "sn");
            g.sId = str(o, "sId");
            g.sType = i(o, "sType", 0);
            g.lat = d(o, "lat", 0);
            g.lng = d(o, "lng", 0);
            g.tag = str(o, "tag");
            g.targetStation = Stop.from(o.optJSONObject("targetStation"));
            JSONArray ls = o.optJSONArray("lines");
            if (ls != null) {
                for (int k = 0; k < ls.length(); k++) g.lines.add(NearLine.from(ls.optJSONObject(k)));
            }
            return g;
        }
    }

    public static final class NearLines {
        public List<NearGroup> groups = new ArrayList<>();
        public String toast = "";

        public static NearLines from(JSONObject o) {
            NearLines n = new NearLines();
            if (o == null) return n;
            JSONArray gs = o.optJSONArray("nearLines");
            if (gs != null) {
                for (int k = 0; k < gs.length(); k++) n.groups.add(NearGroup.from(gs.optJSONObject(k)));
            }
            n.toast = str(o, "toast");
            return n;
        }
    }

    /** 附近物理站点（用于把附近线路分组解析成可打开的站点详情）。 */
    public static final class NearStation {
        public String sId = "";
        public String sn = "";
        public String physicalStId = "";
        public String namesakeStId = "";
        public int distance;
        public double lat;
        public double lng;
        public int sType;

        public static NearStation from(JSONObject o) {
            NearStation s = new NearStation();
            if (o == null) return s;
            s.sId = str(o, "sId");
            s.sn = str(o, "sn");
            s.physicalStId = str(o, "physicalStId");
            s.namesakeStId = str(o, "namesakeStId");
            s.distance = i(o, "distance", 0);
            s.lat = d(o, "lat", 0);
            s.lng = d(o, "lng", 0);
            s.sType = i(o, "sType", 0);
            return s;
        }
    }

    public static final class NearStations {
        public List<NearStation> stations = new ArrayList<>();

        public static NearStations from(JSONObject o) {
            NearStations n = new NearStations();
            if (o == null) return n;
            JSONArray arr = o.optJSONArray("nearStations");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) n.stations.add(NearStation.from(arr.optJSONObject(k)));
            }
            return n;
        }
    }

    public static final class StationLine {
        public Line line = new Line();
        public Stop targetStation = new Stop();
        public List<StnState> stnStates = new ArrayList<>();
        public int depIntervalM = -1;

        public static StationLine from(JSONObject o) {
            StationLine s = new StationLine();
            if (o == null) return s;
            s.line = Line.from(o.optJSONObject("line"));
            s.targetStation = Stop.from(o.optJSONObject("targetStation"));
            s.depIntervalM = i(o, "depIntervalM", -1);
            JSONArray ss = o.optJSONArray("stnStates");
            if (ss != null) {
                for (int k = 0; k < ss.length(); k++) s.stnStates.add(StnState.from(ss.optJSONObject(k)));
            }
            return s;
        }
    }

    public static final class StationEntry {
        public double distance;
        public double lat;
        public double lng;
        public String physicalStId = "";
        public String sId = "";
        public List<StationLine> lines = new ArrayList<>();

        public static StationEntry from(JSONObject o) {
            StationEntry e = new StationEntry();
            if (o == null) return e;
            e.distance = d(o, "distance", 0);
            e.lat = d(o, "lat", 0);
            e.lng = d(o, "lng", 0);
            e.physicalStId = str(o, "physicalStId");
            e.sId = str(o, "sId");
            JSONArray ls = o.optJSONArray("lines");
            if (ls != null) {
                for (int k = 0; k < ls.length(); k++) e.lines.add(StationLine.from(ls.optJSONObject(k)));
            }
            return e;
        }
    }

    public static final class StationDetail {
        public List<StationEntry> entries = new ArrayList<>();

        public static StationDetail from(JSONObject o) {
            StationDetail d = new StationDetail();
            if (o == null) return d;
            JSONArray list = o.optJSONArray("stationList");
            if (list != null) {
                for (int k = 0; k < list.length(); k++) d.entries.add(StationEntry.from(list.optJSONObject(k)));
            }
            return d;
        }
    }

    public static final class TimetableSegment {
        public String fromTime = "";
        public String toTime = "";
        public int depInterval = -1;
        public String intervalTips = "";
        public String forecastDesc = "";
        public List<String> times = new ArrayList<>();

        public static TimetableSegment from(JSONObject o) {
            TimetableSegment t = new TimetableSegment();
            if (o == null) return t;
            t.fromTime = str(o, "fTime");
            t.toTime = str(o, "eTime");
            t.depInterval = i(o, "depInterval", -1);
            t.intervalTips = str(o, "depIntervalTips");
            t.forecastDesc = str(o, "forecastDesc");
            JSONArray ts = o.optJSONArray("times");
            if (ts != null) {
                for (int k = 0; k < ts.length(); k++) t.times.add(ts.optString(k));
            }
            return t;
        }
    }

    public static final class Timetable {
        public String timeTip = "";
        public String tip = "";
        public String depTips = "";
        public List<TimetableSegment> segments = new ArrayList<>();

        public static Timetable from(JSONObject o) {
            Timetable t = new Timetable();
            if (o == null) return t;
            t.timeTip = str(o, "timeTip");
            t.tip = str(o, "tip");
            t.depTips = str(o, "depTips");
            JSONArray seg = o.optJSONArray("timetable");
            if (seg != null) {
                for (int k = 0; k < seg.length(); k++) t.segments.add(TimetableSegment.from(seg.optJSONObject(k)));
            }
            return t;
        }
    }
    /** 官方地名搜索返回的 POI。 */
    public static final class Poi {
        public String address = "";
        public String adname = "";
        public String sn = "";
        public String sn1 = "";
        public String sn1Address = "";
        public String sn1Tag = "";
        public int sn1Type;
        public double lat;
        public double lng;

        public static Poi from(JSONObject o) {
            Poi p = new Poi();
            if (o == null) return p;
            p.address = str(o, "address");
            p.adname = str(o, "adname");
            p.sn = str(o, "sn");
            p.sn1 = str(o, "sn1");
            p.sn1Address = str(o, "sn1Address");
            p.sn1Tag = str(o, "sn1Tag");
            p.sn1Type = i(o, "sn1Type", 0);
            p.lat = d(o, "lat", 0);
            p.lng = d(o, "lng", 0);
            return p;
        }

        public String title() {
            return sn1.isEmpty() ? sn : sn1;
        }
    }

    /** 地名搜索里的公交站候选。 */
    public static final class PlaceStation {
        public String sId = "";
        public String sn = "";
        public String gpsType = "";
        public double lat;
        public double lng;
        public int distance;
        public int stopNum;
        public int subwayV2;

        public static PlaceStation from(JSONObject o) {
            PlaceStation s = new PlaceStation();
            if (o == null) return s;
            s.sId = str(o, "sId");
            s.sn = str(o, "sn");
            s.gpsType = str(o, "gpsType");
            s.lat = d(o, "lat", 0);
            s.lng = d(o, "lng", 0);
            s.distance = i(o, "distance", 0);
            s.stopNum = i(o, "stopNum", 0);
            s.subwayV2 = i(o, "subwayV2", 0);
            return s;
        }
    }

    /** /api/bus/query!encryptedNSearch.action 解密后的结果。 */
    public static final class PlaceSearch {
        public String gpsType = "";
        public String highlightKey = "";
        public List<Line> lines = new ArrayList<>();
        public List<Poi> pois = new ArrayList<>();
        public List<PlaceStation> stations = new ArrayList<>();

        public static PlaceSearch from(JSONObject o) {
            PlaceSearch p = new PlaceSearch();
            if (o == null) return p;
            p.highlightKey = str(o, "highlightKey");
            JSONObject r = o.optJSONObject("result");
            if (r == null) r = o;
            p.gpsType = str(r, "gpstype");
            JSONArray ls = r.optJSONArray("lines");
            if (ls != null) {
                for (int i = 0; i < ls.length(); i++) p.lines.add(Line.from(ls.optJSONObject(i)));
            }
            JSONArray pois = r.optJSONArray("pois");
            if (pois != null) {
                for (int i = 0; i < pois.length(); i++) p.pois.add(Poi.from(pois.optJSONObject(i)));
            }
            JSONArray st = r.optJSONArray("stations");
            if (st != null) {
                for (int i = 0; i < st.length(); i++) p.stations.add(PlaceStation.from(st.optJSONObject(i)));
            }
            return p;
        }

        public int count() {
            return lines.size() + pois.size() + stations.size();
        }
    }

}
