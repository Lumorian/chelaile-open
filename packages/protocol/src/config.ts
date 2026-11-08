/** 车来了 H5 接口的固定常量。逆向自 web.chelaile.net.cn 线上流量，详见 docs/02-协议参考.md。 */

export const DEFAULT_BASE_URL = "https://web.chelaile.net.cn";

/** H5 端公共参数（服务端不校验，但缺少时部分接口返回 status=400）。 */
export const H5_PARAMS = {
  s: "h5",
  v: "9.1.2",
  vc: "1",
  src: "wechat_shaoguan",
  sign: "1"
} as const;

/** cryptoSign 的固定盐值。 */
export const SIGN_SALT = "qwihrnbtmj";

/** encryptResult 的 AES-256-ECB 密钥（ASCII 32 字节）。 */
export const AES_KEY = "422556651C7F7B2B5C266EED06068230";

export const DEFAULT_UA =
  "Mozilla/5.0 (Linux; Android 13; PKM110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36";

/** 城市列表接口用的“老 App”参数（与 /api/ 前缀无关）。 */
export const CITY_PARAMS = {
  s: "android",
  v: "3.80.0",
  src: "webapp_default"
} as const;
