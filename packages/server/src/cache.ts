/** 极简 TTL 缓存 + 请求合并（同一 key 的并发请求只打一次上游）。 */

interface Entry<T> {
  value: T;
  expires: number;
}

export class TtlCache {
  private store = new Map<string, Entry<unknown>>();
  private inflight = new Map<string, Promise<unknown>>();
  private readonly maxEntries: number;

  constructor(maxEntries = 500) {
    this.maxEntries = maxEntries;
  }

  get size(): number {
    return this.store.size;
  }

  get<T>(key: string): T | undefined {
    const hit = this.store.get(key);
    if (!hit) return undefined;
    if (hit.expires < Date.now()) {
      this.store.delete(key);
      return undefined;
    }
    // 命中即刷新 LRU 顺序
    this.store.delete(key);
    this.store.set(key, hit);
    return hit.value as T;
  }

  set<T>(key: string, value: T, ttlMs: number): void {
    if (this.store.size >= this.maxEntries) {
      const oldest = this.store.keys().next().value;
      if (oldest !== undefined) this.store.delete(oldest);
    }
    this.store.set(key, { value, expires: Date.now() + ttlMs });
  }

  /** 读缓存，未命中则执行 loader；并发同 key 只执行一次。 */
  async wrap<T>(key: string, ttlMs: number, loader: () => Promise<T>): Promise<T> {
    const cached = this.get<T>(key);
    if (cached !== undefined) return cached;
    const running = this.inflight.get(key) as Promise<T> | undefined;
    if (running) return running;
    const task = loader()
      .then((value) => {
        this.set(key, value, ttlMs);
        return value;
      })
      .finally(() => {
        this.inflight.delete(key);
      });
    this.inflight.set(key, task);
    return task;
  }

  clear(): void {
    this.store.clear();
    this.inflight.clear();
  }
}
