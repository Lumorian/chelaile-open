import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { ChelaileClient, ApiError } from "../../protocol/src/index.ts";
import { TtlCache } from "./cache.ts";
import * as routes from "./routes.ts";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const REPO_ROOT = resolve(HERE, "../../..");
const WEB_DIST = join(REPO_ROOT, "apps/web/dist");

const PORT = Number(process.env.PORT ?? 8787);
const HOST = process.env.HOST ?? "127.0.0.1";

const client = new ChelaileClient({
  baseUrl: process.env.CHELAILE_BASE_URL ?? undefined,
  timeoutMs: 12000
});

const cache = new TtlCache(800);
const ctx: routes.RequestContext = { client, cache };

const MIME: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".map": "application/json; charset=utf-8"
};

interface Ctx {
  url: URL;
  req: IncomingMessage;
  res: ServerResponse;
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Cache-Control": "no-store"
  });
  res.end(payload);
}

function num(url: URL, key: string, fallback?: number): number {
  const raw = url.searchParams.get(key);
  if (raw === null || raw === "") {
    if (fallback !== undefined) return fallback;
    throw new BadRequest("缺少参数 " + key);
  }
  const value = Number(raw);
  if (!Number.isFinite(value)) throw new BadRequest("参数 " + key + " 不是数字");
  return value;
}

function str(url: URL, key: string, fallback?: string): string {
  const raw = url.searchParams.get(key);
  if (raw === null || raw === "") {
    if (fallback !== undefined) return fallback;
    throw new BadRequest("缺少参数 " + key);
  }
  return raw;
}

class BadRequest extends Error {}

/** API 路由表：路径 → 处理函数。返回值直接序列化为 {ok:true,data,...}。 */
async function handleApi(path: string, url: URL) {
  if (path === "/api/health") {
    return { ok: true, data: { status: "ok", upstream: client.baseUrl, time: new Date().toISOString() } };
  }
  if (path === "/api/cities") {
    return { ok: true, ...(await routes.getCities(ctx)) };
  }
  if (path === "/api/locate") {
    const lat = num(url, "lat");
    const lng = num(url, "lng");
    return { ok: true, ...(await routes.locateCity(ctx, lat, lng)) };
  }
  if (path === "/api/lines") {
    const cityId = str(url, "cityId", "241");
    const q = str(url, "q", "");
    const limit = num(url, "limit", 40);
    return { ok: true, ...(await routes.searchLines(ctx, cityId, q, limit)) };
  }
  if (path === "/api/nearby/lines") {
    const cityId = str(url, "cityId", "241");
    const lat = num(url, "lat");
    const lng = num(url, "lng");
    return { ok: true, ...(await routes.nearbyLines(ctx, cityId, lat, lng)) };
  }
  if (path === "/api/nearby/stations") {
    const cityId = str(url, "cityId", "241");
    const lat = num(url, "lat");
    const lng = num(url, "lng");
    return { ok: true, ...(await routes.nearbyStations(ctx, cityId, lat, lng)) };
  }
  const lineRoute = path.match(/^\/api\/line\/([^/]+)\/route$/);
  if (lineRoute) {
    const cityId = str(url, "cityId", "241");
    return { ok: true, ...(await routes.lineRoute(ctx, decodeURIComponent(lineRoute[1]), cityId)) };
  }
  const lineDetail = path.match(/^\/api\/line\/([^/]+)\/detail$/);
  if (lineDetail) {
    const cityId = str(url, "cityId", "241");
    return {
      ok: true,
      ...(await routes.lineDetail(ctx, cityId, {
        lineId: decodeURIComponent(lineDetail[1]),
        lineName: str(url, "lineName", ""),
        direction: str(url, "direction", "0"),
        stationName: str(url, "stationName", ""),
        nextStationName: str(url, "nextStationName", ""),
        lineNo: str(url, "lineNo", ""),
        targetOrder: str(url, "targetOrder", "0")
      }))
    };
  }
  const station = path.match(/^\/api\/station\/([^/]+)$/);
  if (station) {
    const cityId = str(url, "cityId", "241");
    const name = str(url, "name", "");
    return { ok: true, ...(await routes.stationDetail(ctx, cityId, decodeURIComponent(station[1]), name)) };
  }
  return null;
}

async function serveStatic(path: string, res: ServerResponse): Promise<boolean> {
  if (!existsSync(WEB_DIST)) return false;
  const rel = path === "/" ? "/index.html" : path;
  let file = join(WEB_DIST, rel);
  if (!file.startsWith(WEB_DIST)) return false;
  if (!existsSync(file) || !extname(file)) file = join(WEB_DIST, "index.html");
  try {
    const buf = await readFile(file);
    res.writeHead(200, {
      "Content-Type": MIME[extname(file)] ?? "application/octet-stream",
      "Content-Length": buf.length,
      "Cache-Control": path.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-cache"
    });
    res.end(buf);
    return true;
  } catch {
    return false;
  }
}

const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
  const started = Date.now();
  const url = new URL(req.url ?? "/", "http://" + (req.headers.host ?? "localhost"));
  const path = url.pathname;

  // CORS：本地开发时 vite 跑在另一个端口，直接放开。
  res.setHeader("Access-Control-Allow-Origin", req.headers.origin ?? "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  res.setHeader("Vary", "Origin");
  if (req.method === "OPTIONS") {
    res.writeHead(204).end();
    return;
  }
  if (req.method !== "GET" && req.method !== "HEAD") {
    sendJson(res, 405, { ok: false, error: "只支持 GET" });
    return;
  }

  if (path.startsWith("/api/")) {
    try {
      const result = await handleApi(path, url);
      if (!result) {
        sendJson(res, 404, { ok: false, error: "未知接口: " + path });
        return;
      }
      sendJson(res, 200, result);
    } catch (err) {
      const status = err instanceof BadRequest ? 400 : 502;
      const body = {
        ok: false,
        error: err instanceof Error ? err.message : String(err),
        code: err instanceof ApiError ? err.status : err instanceof HttpErrorLike ? String(err.status) : undefined
      };
      sendJson(res, status, body);
    } finally {
      const ms = Date.now() - started;
      if (process.env.CHELAILE_LOG !== "0") console.log(`${req.method} ${url.pathname}${url.search} ${ms}ms`);
    }
    return;
  }

  if (await serveStatic(path, res)) return;
  res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
  res.end("404 Not Found\n提示：先执行 npm run build 生成前端静态文件。\n");
});

class HttpErrorLike extends Error {
  status?: number;
}

server.listen(PORT, HOST, () => {
  console.log(`车来了开放版服务已启动: http://${HOST}:${PORT}`);
  console.log(`上游接口: ${client.baseUrl}`);
  if (!existsSync(WEB_DIST)) console.log("（未找到 apps/web/dist，仅提供 /api）");
});

for (const sig of ["SIGINT", "SIGTERM"] as const) {
  process.on(sig, () => {
    console.log("\n正在关闭…");
    server.close(() => process.exit(0));
  });
}