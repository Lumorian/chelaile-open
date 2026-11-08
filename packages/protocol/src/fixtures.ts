/**
 * 演示数据（Demo fixtures）。
 *
 * 用途：离线开发 / 无实时车辆时段截图 / 单元测试。所有内容都是**编造**的，
 * 站点名与线路编号参照韶关 38 路的真实形态，但车辆位置、到站时间均为虚构。
 */
import type {
  City,
  LineDetailData,
  LineRouteData,
  LineSummary,
  NearLinesData,
  NearStationsData,
  RouteStation,
  StationDetailData
} from "./types.ts";

const GEO = { lat: 24.813761, lng: 113.591965 };

export const demoCities: City[] = [
  { cityId: "241", cityName: "韶关", pinyin: "shaoguan", cityVersion: 20260101, hot: 0, supportSubway: 0 },
  { cityId: "027", cityName: "北京", pinyin: "beijing", cityVersion: 20260101, hot: 1, supportSubway: 1 },
  { cityId: "034", cityName: "上海", pinyin: "shanghai", cityVersion: 20260101, hot: 1, supportSubway: 1 },
  { cityId: "004", cityName: "杭州", pinyin: "hangzhou", cityVersion: 20260101, hot: 1, supportSubway: 1 },
  { cityId: "003", cityName: "重庆", pinyin: "chongqing", cityVersion: 20260101, hot: 1, supportSubway: 1 },
  { cityId: "007", cityName: "成都", pinyin: "chengdu", cityVersion: 20260101, hot: 1, supportSubway: 1 },
  { cityId: "020", cityName: "太原", pinyin: "taiyuan", cityVersion: 20260101, hot: 0, supportSubway: 0 }
];

const NAMES_38 = [
  "碧桂园（凤凰城）", "凤凰城总站", "碧桂园凤凰城", "韶关学院医学院", "十里亭", "五里亭",
  "西河汽车站", "西河", "市一医院", "风采广场（市一医院）", "东堤中路", "解放路",
  "市政府（北苑）", "北江广场", "韶关东站", "南郊", "沙湖公园", "碧桂园（太阳城）"
];

/** 沿一条略微弯曲的路径铺开站点，保证地图/时间轴看起来自然。 */
function stationsOf(names: string[], startLat = GEO.lat - 0.02, startLng = GEO.lng - 0.03): RouteStation[] {
  return names.map((sn, i) => {
    const t = i / Math.max(1, names.length - 1);
    return {
      sId: "demo-38-" + String(i + 1).padStart(3, "0"),
      sn,
      order: i + 1,
      lat: Number((startLat + t * 0.045 + Math.sin(t * 6) * 0.002).toFixed(6)),
      lng: Number((startLng + t * 0.05 + Math.cos(t * 5) * 0.002).toFixed(6)),
      wgsLat: Number((startLat + t * 0.045).toFixed(6)),
      wgsLng: Number((startLng + t * 0.05).toFixed(6)),
      distanceToSp: -1,
      physicalStId: "demo-phy-" + String(i + 1).padStart(3, "0"),
      namesakeStId: "",
      metros: [],
      special: 0,
      specialOrder: 0,
      gpsType: "wgs"
    };
  });
}

function routePoints(stations: RouteStation[]) {
  const points: LineRouteData["route"] = [];
  for (let i = 0; i < stations.length - 1; i++) {
    const a = stations[i];
    const b = stations[i + 1];
    for (let k = 0; k < 4; k++) {
      const t = k / 4;
      points.push({
        lat: Number((a.lat + (b.lat - a.lat) * t).toFixed(6)),
        lng: Number((a.lng + (b.lng - a.lng) * t).toFixed(6)),
        stopOrder: a.order,
        distance: Math.round(t * 400)
      });
    }
  }
  const last = stations[stations.length - 1];
  points.push({ lat: last.lat, lng: last.lng, stopOrder: last.order, distance: 0 });
  return points;
}

const STATIONS_38 = stationsOf(NAMES_38);
const TARGET = STATIONS_38[10];

export const demoLineSummaries: LineSummary[] = [
  { lineId: "demo-751135962641", lineName: "38", startStopName: "碧桂园（凤凰城）", endStopName: "碧桂园（太阳城）", firstTime: "06:50", lastTime: "17:20", type: 0 },
  { lineId: "demo-751135962642", lineName: "38", startStopName: "碧桂园（太阳城）", endStopName: "碧桂园（凤凰城）", firstTime: "06:50", lastTime: "17:35", type: 0 },
  { lineId: "demo-751135962652", lineName: "1", startStopName: "凤凰城总站", endStopName: "森鑫汽车城", firstTime: "06:20", lastTime: "22:00", type: 0 },
  { lineId: "demo-751135962653", lineName: "2", startStopName: "韶关东站（站台②）", endStopName: "十里亭公交总站", firstTime: "06:30", lastTime: "22:00", type: 0 },
  { lineId: "demo-751135962654", lineName: "14", startStopName: "韶州公园（西门）", endStopName: "华南农产品交易中心", firstTime: "06:30", lastTime: "18:00", type: 0 }
];

function nearLine(lineId: string, lineName: string, startSn: string, endSn: string, minutes: number[], depIntervalM = -1) {
  return {
    line: {
      lineId,
      name: lineName,
      lineNo: "2410080",
      startSn,
      endSn,
      destinationName: endSn,
      direction: 1,
      firstTime: "06:20",
      lastTime: "22:00",
      price: "2元",
      stationsNum: 26,
      desc: minutes.length ? "车辆行驶中" : "首班未发",
      shortDesc: minutes.length ? "" : "首班未发",
      assistDesc: minutes.length ? "距您 3 站" : "",
      nextOperationTimeDesc: minutes.length ? "" : "首班 06:20",
      state: minutes.length ? 1 : -4,
      hasBusInfo: minutes.length > 0,
      segTimes: [["06:20", "22:00"]] as Array<[string, string]>,
      tags: { company: [528, 177] }
    },
    targetStation: { sId: "demo-38-011", sn: "东堤中路", order: 10, distanceToSp: 139, lat: TARGET.lat, lng: TARGET.lng, couponFlag: 0 },
    nextStation: { sId: "demo-38-012", sn: "解放路", order: 11, distanceToSp: -1, lat: TARGET.lat, lng: TARGET.lng, couponFlag: 0 },
    stnStates: minutes.map((time, i) => ({ order: 10 - i, sId: "demo-38-0" + (10 - i), time: time * 60, distance: i * 400, buses: [] })),
    depIntervalM,
    intervalLeft: 0,
    intervalRight: 0,
    traffic: 0,
    priority: 0
  };
}

export function demoNearLines(): NearLinesData {
  return {
    nearLines: [
      {
        allLineSize: 3,
        distance: 139,
        lat: 24.814616,
        lng: 113.592976,
        lines: [
          nearLine("demo-751135962641", "38", "碧桂园（凤凰城）", "碧桂园（太阳城）", [3, 13, 24]),
          nearLine("demo-751135962652", "1", "凤凰城总站", "森鑫汽车城", [7, 19]),
          nearLine("demo-751135962653", "2", "韶关东站（站台②）", "十里亭公交总站", [11])
        ]
      },
      {
        allLineSize: 2,
        distance: 142,
        lat: 24.8151,
        lng: 113.5932,
        lines: [
          nearLine("demo-751135962654", "14", "韶州公园（西门）", "华南农产品交易中心", []),
          nearLine("demo-751135962655", "12", "韶关东站（站台⑥）", "韶关东站（站台⑥）", [5, 16], 29)
        ]
      },
      {
        allLineSize: 1,
        distance: 398,
        lat: 24.8169,
        lng: 113.5944,
        lines: [nearLine("demo-751135962656", "7", "韶关学院", "武江区政府", [2, 9])]
      }
    ],
    ads: [],
    toast: "",
    saveFav: null
  };
}

export function demoNearStations(): NearStationsData {
  return {
    nearStations: [
      { sId: "demo-38-011", sn: "东堤中路", distance: 139, lat: 24.814616, lng: 113.592976, sType: 0, physicalStId: "demo-phy-011", namesakeStId: "" },
      { sId: "demo-38-012", sn: "解放路", distance: 280, lat: 24.8157, lng: 113.5938, sType: 0, physicalStId: "demo-phy-012", namesakeStId: "" },
      { sId: "demo-38-013", sn: "市政府（北苑）", distance: 398, lat: 24.8169, lng: 113.5944, sType: 0, physicalStId: "demo-phy-013", namesakeStId: "" },
      { sId: "demo-38-014", sn: "北江广场", distance: 446, lat: 24.8178, lng: 113.5952, sType: 0, physicalStId: "demo-phy-014", namesakeStId: "" }
    ],
    userLat: GEO.lat,
    userLng: GEO.lng,
    gpstype: "wgs",
    centerStation: { sId: "demo-38-011", sn: "东堤中路", lat: 24.814616, lng: 113.592976, distance: 139, sType: 0 }
  };
}

export function demoLineRoute(lineId = "demo-751135962641"): LineRouteData {
  return {
    route: routePoints(STATIONS_38),
    line: { lineId, name: "38" },
    stations: STATIONS_38,
    gpstype: "wgs"
  };
}

export function demoLineDetail(lineId = "demo-751135962641", targetOrder = 10): LineDetailData {
  const near = nearLine(lineId, "38", "碧桂园（凤凰城）", "碧桂园（太阳城）", [3, 13, 24]);
  return {
    line: near.line,
    stations: STATIONS_38,
    buses: [
      { busId: "demo-bus-1", order: 8, distance: 800, time: 180, lat: 24.8151, lng: 113.5929, plate: "粤F·12345" },
      { busId: "demo-bus-2", order: 5, distance: 2400, time: 780, lat: 24.8146, lng: 113.5902, plate: "粤F·67890" },
      { busId: "demo-bus-3", order: 2, distance: 5200, time: 1440, lat: 24.8128, lng: 113.5871, plate: "粤F·24680" }
    ],
    targetOrder,
    nearStnOrder: targetOrder,
    realData: true,
    refreshInterval: 15,
    intervalLeft: 0,
    intervalRight: 0,
    depIntervalM: -1,
    depDesc: "",
    preArrivalTime: "",
    predictionText: "预计 3 分钟后到站",
    tip: { desc: "演示数据", destType: 0 },
    otherlines: []
  };
}

export function demoStationDetail(physicalStId = "demo-phy-011", name = "东堤中路"): StationDetailData {
  return {
    menuStyle: 1,
    stationList: [
      {
        distance: -1,
        lat: 24.814616,
        lng: 113.592976,
        lines: [
          nearLine("demo-751135962641", "38", "碧桂园（凤凰城）", "碧桂园（太阳城）", [3, 13]),
          nearLine("demo-751135962652", "1", "凤凰城总站", "森鑫汽车城", [7]),
          nearLine("demo-751135962653", "2", "韶关东站（站台②）", "十里亭公交总站", [11])
        ],
        sn: name,
        physicalStId,
        sId: "demo-38-011",
        sType: 0,
        tag: "",
        namesakeStId: ""
      }
    ]
  };
}

export function demoSearchLines(keyword: string): LineSummary[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return [];
  return demoLineSummaries.filter((line) => line.lineName.toLowerCase().includes(kw));
}