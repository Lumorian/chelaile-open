/** 线上响应有时被 "**YGKJ ... YGKJ##" 包裹（网关加的防 JSONP 前缀），这里统一剥壳。 */

export interface JsonrEnvelope<T> {
  jsonr: {
    status?: string;
    errmsg?: string | null;
    data?: T;
    sversion?: string | null;
  };
}

export function stripWrapper(text: string): string {
  const t = text.trim();
  const start = t.indexOf("**YGKJ");
  if (start === -1) return t;
  let body = t.slice(start + 6);
  const end = body.lastIndexOf("YGKJ##");
  if (end !== -1) body = body.slice(0, end);
  return body.trim();
}
