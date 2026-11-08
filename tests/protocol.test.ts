/**
 * 协议层单元测试（离线，可直接 `npm test` 运行）。
 *
 * 覆盖：MD5、AES-256-ECB 解密（含一份真实线上密文）、网关包裹剥壳、cryptoSign、
 * 以及 UI 用的文案聚合函数。真实网络请求在 tests/live.test.ts，需 CHELAILE_LIVE=1。
 */
import test from "node:test";
import assert from "node:assert/strict";
import { createCipheriv, createHash, randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  AES_KEY,
  SIGN_SALT,
  aesEcbDecrypt,
  base64ToBytes,
  cryptoSign,
  formatClock,
  formatDistance,
  lineTitle,
  md5Hex,
  operationWindow,
  stripWrapper,
  summarize,
  bd09ToGcj02,
  bd09ToWgs84,
  gcj02ToBd09,
  gcj02ToWgs84,
  outOfChina,
  stationToWgs,
  wgs84ToGcj02,
  type NearLine
} from "../packages/protocol/src/index.ts";

const here = fileURLToPath(new URL(".", import.meta.url));

test("md5：RFC 1321 标准向量", () => {
  const vectors: Array<[string, string]> = [
    ["", "d41d8cd98f00b204e9800998ecf8427e"],
    ["a", "0cc175b9c0f1b6a831c399e269772661"],
    ["abc", "900150983cd24fb0d6963f7d28e17f72"],
    ["message digest", "f96b697d7cb7938d525a2f31aaf161d0"],
    ["abcdefghijklmnopqrstuvwxyz", "c3fcd3d76192e4007dfb496cca67e13b"],
    ["12345678901234567890123456789012345678901234567890123456789012345678901234567890", "57edf4a22be3c955ac49da2e2107b67a"]
  ];
  for (const [input, expected] of vectors) assert.equal(md5Hex(input), expected, input);

  // 中文与长输入：与 node:crypto 对拍
  for (const sample of ["车来了", "首班车尚未发车", "x".repeat(1000), "混合 mixed 内容 123"]) {
    assert.equal(md5Hex(sample), createHash("md5").update(sample, "utf8").digest("hex"));
  }
});

test("aes：AES-256-ECB/PKCS7 与 node:crypto 对拍", () => {
  const key = randomBytes(32);
  const keyString = new TextEncoder().encode(AES_KEY);
  // 配置里的密钥必须是 32 字节 ASCII，否则 AES-256 无从谈起
  assert.equal(keyString.length, 32);

  for (const size of [0, 1, 15, 16, 17, 64, 1000]) {
    const plain = randomBytes(size);
    const cipher = createCipheriv("aes-256-ecb", key, null);
    const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]);
    assert.deepEqual(Buffer.from(aesEcbDecrypt(key, encrypted)), plain, "size=" + size);
  }
});

test("aes：解密真实线上响应（站点详情快照）", () => {
  const raw = JSON.parse(readFileSync(here + "fixtures/station-detail.encrypted.json", "utf8")) as { encryptResult: string };
  const plain = aesEcbDecrypt(new TextEncoder().encode(AES_KEY), base64ToBytes(raw.encryptResult));
  const data = JSON.parse(new TextDecoder().decode(plain)) as {
    stationList: Array<{ sn: string; lines: Array<{ line: { lineId: string; name: string }; targetStation: { sn: string } }> }>;
  };
  assert.ok(data.stationList.length >= 1);
  assert.equal(data.stationList[0].sn, "东堤中路");
  assert.ok(data.stationList[0].lines.length >= 1);
  assert.ok(data.stationList[0].lines[0].line.lineId);
});

test("envelope：剥掉 **YGKJ ... YGKJ## 网关包裹", () => {
  const body = '{"jsonr":{"status":"00","data":{"a":1}}}';
  assert.equal(stripWrapper("**YGKJ" + body + "YGKJ##"), body);
  assert.equal(stripWrapper("  **YGKJ" + body + "YGKJ##\n"), body);
  assert.equal(stripWrapper(body), body);
  assert.equal(stripWrapper('**YGKJ{"jsonr":{}}YGKJ##garbage'), '{"jsonr":{}}');
});

test("cryptoSign：业务参数按序拼接后加盐取 md5", () => {
  const params = { lineId: "751135962641", lineName: "38", direction: 1, targetOrder: 5 };
  const raw = "lineId=751135962641&lineName=38&direction=1&targetOrder=5";
  assert.equal(cryptoSign(params), createHash("md5").update(raw + SIGN_SALT).digest("hex"));
  // 空值被忽略
  assert.equal(cryptoSign({ a: "1", b: "", c: undefined, d: 2 }), createHash("md5").update("a=1&d=2" + SIGN_SALT).digest("hex"));
});

test("base64：自实现解码与 Buffer 一致", () => {
  for (const sample of ["", "a", "ab", "abc", "hello world", "车来了"]) {
    const buf = Buffer.from(sample, "utf8");
    assert.deepEqual(Buffer.from(base64ToBytes(buf.toString("base64"))), buf);
  }
});

const demoLine: NearLine = {
  line: { lineId: "751135962641", name: "38", startSn: "碧桂园（凤凰城）", endSn: "碧桂园（太阳城）", firstTime: "06:50", lastTime: "17:20" },
  targetStation: { sn: "东堤中路", order: 10 },
  stnStates: [],
  depIntervalM: -1
};

test("models：实时到站文案", () => {
  const live: NearLine = { ...demoLine, stnStates: [{ order: 7, time: 180 }] };
  const view = summarize(live);
  assert.equal(view.live, true);
  assert.equal(view.primary, "3分钟");
  assert.equal(view.secondary, "还有3站");
  assert.equal(view.etaSeconds, 180);

  const arriving: NearLine = { ...demoLine, stnStates: [{ order: 10, time: 10 }] };
  assert.equal(summarize(arriving).primary, "即将到站");
});

test("models：无实时车辆时的兜底文案", () => {
  const noBus: NearLine = { ...demoLine, line: { ...demoLine.line, shortDesc: "首班未发", nextOperationTimeDesc: "首班预计 06:50 发车" } };
  const view = summarize(noBus);
  assert.equal(view.live, false);
  assert.equal(view.primary, "首班未发");
  assert.equal(view.secondary, "首班预计 06:50 发车");

  const interval: NearLine = { ...demoLine, depIntervalM: 8 };
  assert.equal(summarize(interval).primary, "约8分钟一班");
});

test("models：距离/时间/标题/运营时间", () => {
  assert.equal(formatDistance(139), "139米");
  assert.equal(formatDistance(1500), "1.5公里");
  assert.equal(formatDistance(23000), "23公里");
  assert.equal(formatDistance(-1), "");
  assert.equal(formatDistance(undefined), "");

  assert.equal(lineTitle({ lineId: "1", name: "1", startSn: "A", endSn: "B" }), "A → B");
  assert.equal(operationWindow({ lineId: "1", name: "1", firstTime: "06:50", lastTime: "17:20" }), "06:50–17:20");
  assert.equal(
    operationWindow({ lineId: "1", name: "1", firstTime: "06:50", lastTime: "21:00", segTimes: [["06:50", "11:00"], ["14:00", "21:00"]] }),
    "06:50–11:00 / 14:00–21:00"
  );

  assert.match(formatClock(new Date(2026, 0, 1, 9, 5)), /09:05/);
});
test("geo：BD-09 / GCJ-02 / WGS-84 互转", () => {
  // 境外不偏移
  assert.equal(outOfChina(35.68, 139.76), true);
  assert.equal(outOfChina(24.81, 113.59), false);
  assert.deepEqual(wgs84ToGcj02(35.68, 139.76), { lat: 35.68, lng: 139.76 });

  // 国内偏移量应在 100–800 米量级（不是 0，也不该是几公里）
  const wgs = { lat: 24.813761, lng: 113.591965 };
  const gcj = wgs84ToGcj02(wgs.lat, wgs.lng);
  const dLat = (gcj.lat - wgs.lat) * 111320;
  const dLng = (gcj.lng - wgs.lng) * 111320 * Math.cos((wgs.lat * Math.PI) / 180);
  const offset = Math.hypot(dLat, dLng);
  assert.ok(offset > 100 && offset < 800, "偏移量 " + offset.toFixed(0) + " 米");

  // 往返精度
  const back = gcj02ToWgs84(gcj.lat, gcj.lng);
  assert.ok(Math.abs(back.lat - wgs.lat) < 1e-6);
  assert.ok(Math.abs(back.lng - wgs.lng) < 1e-6);

  const bd = gcj02ToBd09(gcj.lat, gcj.lng);
  const bdBack = bd09ToGcj02(bd.lat, bd.lng);
  assert.ok(Math.abs(bdBack.lat - gcj.lat) < 1e-6);
  assert.ok(Math.abs(bdBack.lng - gcj.lng) < 1e-6);

  // BD→WGS 与 BD→GCJ→WGS 一致
  const viaBd = bd09ToWgs84(bd.lat, bd.lng);
  assert.ok(Math.abs(viaBd.lat - wgs.lat) < 1e-5);
  assert.ok(Math.abs(viaBd.lng - wgs.lng) < 1e-5);
});

test("geo：stationToWgs 优先使用服务端给的 wgs 坐标", () => {
  const withWgs = stationToWgs({ lat: 24.8355, lng: 113.6096, wgsLat: 24.83, wgsLng: 113.60, gpsType: "bd" });
  assert.deepEqual(withWgs, { lat: 24.83, lng: 113.6 });

  const bdOnly = stationToWgs({ lat: 24.8355, lng: 113.6096, gpsType: "bd" });
  assert.ok(Math.abs(bdOnly.lat - 24.8355) < 0.01 && bdOnly.lat !== 24.8355);

  const plain = stationToWgs({ lat: 24.81, lng: 113.59 });
  assert.deepEqual(plain, { lat: 24.81, lng: 113.59 });
});
