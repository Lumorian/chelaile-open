#!/usr/bin/env node
/**
 * 命令行版「车来了」。
 *
 * 用法：
 *   chelaile cities [关键词]              列出支持的城市
 *   chelaile near [--city 241]            附近线路（默认韶关市政府坐标）
 *   chelaile search <线路名> [--city 241]  搜线路
 *   chelaile line <lineId> [--city 241]    线路详情 + 站序
 *   chelaile station <physicalStId> <站名> 站点到站
 */
import { ChelaileClient, formatDistance, summarize, lineTitle, operationWindow } from "../../../packages/protocol/src/index.ts";

const C = {
  reset: "\u001b[0m",
  dim: "\u001b[2m",
  bold: "\u001b[1m",
  blue: "\u001b[38;5;33m",
  green: "\u001b[38;5;35m",
  yellow: "\u001b[38;5;214m",
  gray: "\u001b[38;5;245m"
};

function parseArgs(argv: string[]) {
  const positional: string[] = [];
  const flags: Record<string, string> = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg.startsWith("--")) {
      const [key, inline] = arg.slice(2).split("=");
      if (inline !== undefined) flags[key] = inline;
      else if (argv[i + 1] && !argv[i + 1].startsWith("--")) flags[key] = argv[++i];
      else flags[key] = "true";
    } else {
      positional.push(arg);
    }
  }
  return { positional, flags };
}

function pad(text: string, width: number): string {
  // 中文按 2 列宽计算
  const w = [...text].reduce((n, ch) => n + (ch.charCodeAt(0) > 255 ? 2 : 1), 0);
  return text + " ".repeat(Math.max(0, width - w));
}

function client(city: string, lat?: string, lng?: string) {
  return new ChelaileClient({ cityId: city, lat: lat ? Number(lat) : undefined, lng: lng ? Number(lng) : undefined });
}

function header(title: string) {
  console.log("\n" + C.bold + C.blue + title + C.reset + "\n" + C.gray + "─".repeat(52) + C.reset);
}

async function cmdCities(keyword: string | undefined) {
  const c = client("241");
  const cities = await c.listCities();
  const rows = keyword ? cities.filter((city) => city.cityName.includes(keyword) || city.pinyin.toLowerCase().includes(keyword.toLowerCase())) : cities;
  header("支持的城市（" + rows.length + "）");
  for (const city of rows.slice(0, 120)) {
    console.log("  " + pad(city.cityName, 8) + C.gray + pad(city.pinyin, 14) + "cityId=" + city.cityId + C.reset);
  }
  if (rows.length > 120) console.log(C.gray + "  … 共 " + rows.length + " 个，可加关键词过滤" + C.reset);
}

async function cmdNear(flags: Record<string, string>) {
  const c = client(flags.city ?? "241", flags.lat, flags.lng);
  const { name } = await c.locateCity();
  const near = await c.getNearbyLines();
  header("附近线路" + (name ? " · " + name : ""));
  const groups = near.nearLines.filter((g) => g.lines.length > 0);
  for (const group of groups) {
    const stop = group.lines[0]?.targetStation;
    console.log(C.bold + (stop?.sn ?? "附近站点") + C.reset + C.gray + "  " + formatDistance(group.distance) + C.reset);
    for (const nearLine of group.lines) {
      const view = summarize(nearLine);
      const color = view.live ? C.green : C.yellow;
      const name2 = pad(nearLine.line.name, 6);
      console.log(
        "  " + C.blue + name2 + C.reset + pad(nearLine.line.startSn + "→" + nearLine.line.endSn, 30) +
        color + pad(view.primary, 10) + C.reset + C.gray + view.secondary + C.reset
      );
    }
  }
  console.log(C.gray + "\n刷新时间 " + new Date().toLocaleTimeString("zh-CN", { hour12: false }) + C.reset);
}

async function cmdSearch(keyword: string | undefined, flags: Record<string, string>) {
  if (!keyword) throw new Error("用法: chelaile search <线路名> [--city 241]");
  const c = client(flags.city ?? "241");
  const lines = await c.searchLines(keyword);
  header("搜索 \"" + keyword + "\" → " + lines.length + " 条");
  for (const line of lines) {
    console.log("  " + C.blue + pad(line.lineName, 8) + C.reset + pad(line.startStopName + " → " + line.endStopName, 34) + C.gray + (line.firstTime ?? "") + (line.lastTime ? "-" + line.lastTime : "") + C.reset);
    console.log("  " + C.gray + "lineId=" + line.lineId + C.reset);
  }
}

async function cmdLine(lineId: string | undefined, flags: Record<string, string>) {
  if (!lineId) throw new Error("用法: chelaile line <lineId> [--city 241]");
  const c = client(flags.city ?? "241");
  const route = await c.getLineRoute(lineId);
  header("线路 " + route.line.name);
  console.log(C.dim + "lineId=" + lineId + " · 共 " + route.stations.length + " 站" + C.reset);
  const target = route.stations[Math.min(Number(flags.target ?? 0), route.stations.length - 1)];
  const next = route.stations[Math.min(route.stations.indexOf(target) + 1, route.stations.length - 1)];
  try {
    const detail = await c.getLineDetail({
      lineId,
      lineName: String(route.line.name),
      direction: 0,
      stationName: target.sn,
      nextStationName: next.sn,
      targetOrder: target.order
    });
    console.log(C.bold + lineTitle(detail.line) + C.reset + C.gray + "  " + operationWindow(detail.line) + (detail.line.price ? " · " + detail.line.price : "") + C.reset);
    const view = summarize({ line: detail.line, stnStates: detail.buses as never, targetStation: target, depIntervalM: detail.depIntervalM });
    console.log("状态：" + (view.live ? C.green : C.yellow) + view.primary + C.reset + (view.secondary ? C.gray + " · " + view.secondary + C.reset : ""));
  } catch (err) {
    console.log(C.yellow + "（详情接口暂不可用：" + (err as Error).message + "）" + C.reset);
  }
  console.log();
  for (const [i, station] of route.stations.entries()) {
    const mark = i === route.stations.indexOf(target) ? C.blue + "▶" + C.reset : " ";
    console.log(" " + mark + " " + C.gray + pad(String(station.order), 4) + C.reset + station.sn + (station.metros?.length ? C.gray + "  地铁 " + station.metros.map((m) => m.fullName).join("/") + C.reset : ""));
  }
}

async function cmdStation(physicalStId: string | undefined, name: string | undefined, flags: Record<string, string>) {
  if (!physicalStId || !name) throw new Error("用法: chelaile station <physicalStId> <站名> [--city 241]");
  const c = client(flags.city ?? "241");
  const detail = await c.getStationDetail(physicalStId, name);
  header("站点 " + name);
  for (const group of detail.stationList) {
    for (const nearLine of group.lines) {
      const view = summarize(nearLine);
      console.log("  " + C.blue + pad(nearLine.line.name, 6) + C.reset + pad(nearLine.line.startSn + "→" + nearLine.line.endSn, 30) + (view.live ? C.green : C.yellow) + pad(view.primary, 10) + C.reset + C.gray + view.secondary + C.reset);
    }
  }
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const { positional, flags } = parseArgs(rest);
  try {
    switch (command) {
      case "cities":
        await cmdCities(positional[0]);
        break;
      case "near":
        await cmdNear(flags);
        break;
      case "search":
        await cmdSearch(positional[0], flags);
        break;
      case "line":
        await cmdLine(positional[0], flags);
        break;
      case "station":
        await cmdStation(positional[0], positional[1], flags);
        break;
      default:
        console.log(`车来了 开放版 CLI

用法:
  chelaile cities [关键词]
  chelaile near    [--city 241] [--lat 24.81 --lng 113.59]
  chelaile search  <线路名> [--city 241]
  chelaile line    <lineId> [--city 241] [--target 5]
  chelaile station <physicalStId> <站名> [--city 241]
`);
    }
  } catch (err) {
    console.error(C.yellow + "出错了: " + (err as Error).message + C.reset);
    process.exitCode = 1;
  }
}

await main();