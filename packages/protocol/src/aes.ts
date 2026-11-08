/**
 * Minimal AES (128/192/256) block cipher, decrypt-only.
 *
 * 为什么不用 WebCrypto：浏览器端的 SubtleCrypto 不支持 AES-ECB，
 * 而车来了 H5 接口的 encryptResult 正是 AES-256-ECB/PKCS7。
 * 这里实现一个不依赖任何第三方库的纯 TS 版本，Node 与浏览器通用。
 */

const SBOX = new Uint8Array([
  0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
  0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
  0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
  0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
  0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
  0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
  0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
  0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
  0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
  0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
  0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
  0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
  0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
  0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
  0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
  0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
]);

const INV_SBOX = (() => {
  const t = new Uint8Array(256);
  for (let i = 0; i < 256; i++) t[SBOX[i]] = i;
  return t;
})();

const MUL2 = new Uint8Array(256);
const MUL3 = new Uint8Array(256);
const MUL9 = new Uint8Array(256);
const MUL11 = new Uint8Array(256);
const MUL13 = new Uint8Array(256);
const MUL14 = new Uint8Array(256);

function xtime(a: number): number {
  return ((a << 1) ^ ((a & 0x80) ? 0x1b : 0)) & 0xff;
}

(function initTables() {
  // 第一遍只填 MUL2：复合表依赖 MUL2 的**全部**项，
  // 若与 MUL2 同步填充会读到尚未赋值的 0（曾导致解密全错）。
  for (let i = 0; i < 256; i++) MUL2[i] = xtime(i);
  for (let i = 0; i < 256; i++) {
    const x2 = MUL2[i];
    const x4 = MUL2[x2];
    const x8 = MUL2[x4];
    MUL3[i] = x2 ^ i;
    MUL9[i] = x8 ^ i;
    MUL11[i] = x8 ^ x2 ^ i;
    MUL13[i] = x8 ^ x4 ^ i;
    MUL14[i] = x8 ^ x4 ^ x2;
  }
})();

function expandKey(key: Uint8Array): Uint8Array {
  const nk = key.length / 4;
  const nr = nk + 6;
  const w = new Uint8Array(16 * (nr + 1));
  w.set(key, 0);
  let rcon = 1;
  for (let i = nk; i < 4 * (nr + 1); i++) {
    const off = i * 4;
    let t0 = w[off - 4];
    let t1 = w[off - 3];
    let t2 = w[off - 2];
    let t3 = w[off - 1];
    if (i % nk === 0) {
      const tmp = t0;
      t0 = SBOX[t1] ^ rcon;
      t1 = SBOX[t2];
      t2 = SBOX[t3];
      t3 = SBOX[tmp];
      rcon = xtime(rcon);
    } else if (nk > 6 && i % nk === 4) {
      t0 = SBOX[t0];
      t1 = SBOX[t1];
      t2 = SBOX[t2];
      t3 = SBOX[t3];
    }
    w[off] = w[off - nk * 4] ^ t0;
    w[off + 1] = w[off - nk * 4 + 1] ^ t1;
    w[off + 2] = w[off - nk * 4 + 2] ^ t2;
    w[off + 3] = w[off - nk * 4 + 3] ^ t3;
  }
  return w;
}

const state = new Uint8Array(16);

function addRoundKey(w: Uint8Array, round: number) {
  const off = round * 16;
  for (let i = 0; i < 16; i++) state[i] ^= w[off + i];
}

function invShiftRows() {
  let t = state[13];
  state[13] = state[9];
  state[9] = state[5];
  state[5] = state[1];
  state[1] = t;
  t = state[2];
  state[2] = state[10];
  state[10] = t;
  t = state[6];
  state[6] = state[14];
  state[14] = t;
  t = state[3];
  state[3] = state[7];
  state[7] = state[11];
  state[11] = state[15];
  state[15] = t;
}

function invSubBytes() {
  for (let i = 0; i < 16; i++) state[i] = INV_SBOX[state[i]];
}

function invMixColumns() {
  for (let c = 0; c < 4; c++) {
    const i = c * 4;
    const a0 = state[i];
    const a1 = state[i + 1];
    const a2 = state[i + 2];
    const a3 = state[i + 3];
    state[i] = MUL14[a0] ^ MUL11[a1] ^ MUL13[a2] ^ MUL9[a3];
    state[i + 1] = MUL9[a0] ^ MUL14[a1] ^ MUL11[a2] ^ MUL13[a3];
    state[i + 2] = MUL13[a0] ^ MUL9[a1] ^ MUL14[a2] ^ MUL11[a3];
    state[i + 3] = MUL11[a0] ^ MUL13[a1] ^ MUL9[a2] ^ MUL14[a3];
  }
}

function decryptBlock(w: Uint8Array, nr: number, input: Uint8Array, inputOff: number, out: Uint8Array, outOff: number) {
  for (let i = 0; i < 16; i++) state[i] = input[inputOff + i];
  addRoundKey(w, nr);
  for (let round = nr - 1; round >= 1; round--) {
    invShiftRows();
    invSubBytes();
    addRoundKey(w, round);
    invMixColumns();
  }
  invShiftRows();
  invSubBytes();
  addRoundKey(w, 0);
  for (let i = 0; i < 16; i++) out[outOff + i] = state[i];
}

/** AES-ECB 解密，自动去除 PKCS#7 填充。key 可为 16/24/32 字节。 */
export function aesEcbDecrypt(key: Uint8Array, data: Uint8Array): Uint8Array {
  if (data.length === 0 || data.length % 16 !== 0) throw new Error("密文长度必须是 16 的倍数");
  const w = expandKey(key);
  const nr = key.length / 4 + 6;
  const out = new Uint8Array(data.length);
  for (let off = 0; off < data.length; off += 16) decryptBlock(w, nr, data, off, out, off);
  const pad = out[out.length - 1];
  if (pad < 1 || pad > 16 || pad > out.length) throw new Error("PKCS#7 填充非法");
  for (let i = out.length - pad; i < out.length; i++) {
    if (out[i] !== pad) throw new Error("PKCS#7 填充非法");
  }
  return out.subarray(0, out.length - pad);
}

/** AES-ECB 加密（仅测试/校验用）。 */
export function aesEcbEncrypt(key: Uint8Array, data: Uint8Array): Uint8Array {
  throw new Error("未实现：本库只需要解密方向");
}