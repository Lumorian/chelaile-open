/**
 * 线上联调测试。默认跳过，需要显式开启：
 *
 *   CHELAILE_LIVE=1 npm run test:live
 *
 * 会在韶关（cityId=241）用真实接口跑一遍主链路。凌晨等无车时段部分字段为空属正常。
 */
import test from "node:test";
import assert from "node:assert/strict";
import { ChelaileClient } from "../packages/protocol/src/index.ts";

const enabled = process.env.CHELAILE_LIVE === "1";
const client = new ChelaileClient({ cityId: "241" });

test("城市列表：包含韶关，且能按名称反查", { skip: !enabled }, async () => {
  const cities = await client.listCities();
  assert.ok(cities.length > 400, "城市数量 " + cities.length);
  const shaoguan = cities.find((c) => c.cityName === "韶关");
  assert.ok(shaoguan, "未找到韶关");
  assert.equal(shaoguan.cityId, "241");
});

test("线路检索：韶关 38 路双向都在", { skip: !enabled }, async () => {
  const lines = await client.searchLines("38");
  assert.ok(lines.length >= 2);
  for (const line of lines) assert.ok(line.lineId);
});

test("线路站序：站数、经纬度齐全", { skip: !enabled }, async () => {
  const route = await client.getLineRoute("751135962641");
  assert.ok(route.stations.length > 10);
  assert.ok(route.route.length > 10);
  for (const station of route.stations.slice(0, 5)) {
    assert.ok(station.sn);
    assert.equal(typeof station.order, "number");
  }
});

test("附近线路：解密后可读，含目标站与运营信息", { skip: !enabled }, async () => {
  const near = await client.getNearbyLines();
  assert.ok(near.nearLines.length > 0);
  const withLines = near.nearLines.filter((g) => g.lines.length > 0);
  assert.ok(withLines.length > 0, "附近站点都没有线路");
  const first = withLines[0].lines[0];
  assert.ok(first.line.name);
  assert.ok(first.targetStation?.sn);
});

test("站点详情：按 physicalStId 查询", { skip: !enabled }, async () => {
  const stations = await client.getNearbyStations();
  assert.ok(stations.nearStations.length > 0);
  const target = stations.nearStations[0];
  const detail = await client.getStationDetail(target.physicalStId, target.sn);
  assert.ok(detail.stationList.length > 0);
  assert.ok(detail.stationList[0].lines.length > 0);
});
