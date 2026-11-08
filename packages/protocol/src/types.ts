/**
 * 车来了 H5 接口的原始数据结构（DTO）。
 * 字段名与线上 JSON 完全一致；未完全摸清的字段用可选属性 + 索引签名兜底。
 */

export interface City {
  cityId: string;
  cityName: string;
  pinyin: string;
  cityVersion: number;
  hot: number;
  supportSubway: number;
}

export interface LineSummary {
  lineId: string;
  lineName: string;
  startStopName: string;
  endStopName: string;
  firstTime?: string;
  lastTime?: string;
  tagName?: string;
  tags?: Record<string, unknown>;
  type?: number;
}

export interface RoutePoint {
  lat: number;
  lng: number;
  stopOrder: number;
  distance: number;
}

export interface RouteStation {
  sId: string;
  sn: string;
  order: number;
  lat: number;
  lng: number;
  wgsLat?: number;
  wgsLng?: number;
  distanceToSp?: number;
  gpsType?: string;
  physicalStId?: string;
  namesakeStId?: string;
  metros?: Array<{ color: string; fullName: string; lineId: string; lineNo: string }>;
  special?: number;
  specialOrder?: number;
}

export interface LineRouteData {
  route: RoutePoint[];
  line: { lineId: string; name: string; [k: string]: unknown };
  stations: RouteStation[];
  gpstype?: string;
}

/** 线路基础信息（line 字段），两条链路的并集。 */
export interface LineInfo {
  lineId: string;
  name: string;
  lineNo?: string;
  startSn: string;
  endSn: string;
  destinationName?: string;
  direction?: number;
  firstTime?: string;
  lastTime?: string;
  price?: string;
  stationsNum?: number;
  desc?: string;
  shortDesc?: string;
  assistDesc?: string;
  nextOperationTimeDesc?: string;
  state?: number;
  sfNotOpen?: boolean;
  notNeedRealData?: boolean;
  hasBusInfo?: boolean;
  segTimes?: Array<[string, string]>;
  tags?: Record<string, unknown>;
  [k: string]: unknown;
}

export interface StationRef {
  sId: string;
  sn: string;
  order: number;
  distanceToSp?: number;
  lat?: number;
  lng?: number;
  couponFlag?: number;
}

/** 到站状态（站点维度）。线上字段名不完全稳定，这里全部可选。 */
export interface StnState {
  order?: number;
  sId?: string;
  sn?: string;
  time?: number;
  seconds?: number;
  distance?: number;
  buses?: BusInfo[];
  [k: string]: unknown;
}

export interface BusInfo {
  busId?: string;
  order?: number;
  distance?: number;
  time?: number;
  lat?: number;
  lng?: number;
  plate?: string;
  type?: number;
  [k: string]: unknown;
}

/** /bus/line!encryptedLineDetail 解密后的结构。 */
export interface LineDetailData {
  line: LineInfo;
  stations: RouteStation[];
  buses: BusInfo[];
  targetOrder: number;
  nearStnOrder?: number;
  specialTargetOrder?: number;
  realData?: boolean;
  refreshInterval?: number;
  intervalLeft?: number;
  intervalRight?: number;
  depIntervalM?: number;
  depDesc?: string;
  preArrivalTime?: string;
  predictionText?: string;
  predictionLink?: string;
  tip?: { desc?: string; destType?: number } | Record<string, unknown>;
  toast?: string;
  otherlines?: Array<{ lineId: string; startSn: string; endSn: string; firstTime?: string; lastTime?: string; price?: string }>;
  roads?: unknown;
  traffic?: number;
  [k: string]: unknown;
}

/** nearLines 里每条线路的包装。 */
export interface NearLine {
  line: LineInfo;
  targetStation?: StationRef;
  nextStation?: StationRef;
  stnStates?: StnState[];
  depIntervalM?: number;
  intervalLeft?: number;
  intervalRight?: number;
  traffic?: number;
  priority?: number;
  [k: string]: unknown;
}

export interface NearLineGroup {
  allLineSize?: number;
  distance: number;
  lat?: number;
  lng?: number;
  lines: NearLine[];
  [k: string]: unknown;
}

export interface NearLinesData {
  nearLines: NearLineGroup[];
  ads?: unknown[];
  toast?: string;
  saveFav?: unknown;
  [k: string]: unknown;
}

export interface NearStation {
  sId: string;
  sn: string;
  distance: number;
  lat: number;
  lng: number;
  sType?: number;
  physicalStId?: string;
  namesakeStId?: string;
  [k: string]: unknown;
}

export interface NearStationsData {
  nearStations: NearStation[];
  userLat: number;
  userLng: number;
  gpstype?: string;
  centerStation?: Record<string, unknown>;
  [k: string]: unknown;
}

export interface StationLine extends NearLine {
  distance?: number;
}

export interface PhysicalStationGroup {
  distance: number;
  lat: number;
  lng: number;
  lines: StationLine[];
  [k: string]: unknown;
}

export interface StationDetailData {
  stationList: PhysicalStationGroup[];
  menuStyle?: number;
  [k: string]: unknown;
}
