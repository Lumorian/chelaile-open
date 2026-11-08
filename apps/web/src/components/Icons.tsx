type IconProps = { size?: number };

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true
});

/** 公交车：车身 + 车窗 + 两个轮子 */
export function BusIcon({ size = 22 }: IconProps) {
  return (
    <svg {...base(size)}>
      <rect x="3" y="4" width="18" height="13" rx="3" />
      <path d="M3 10h18" />
      <path d="M7 4v6M17 4v6" opacity=".45" />
      <circle cx="7.5" cy="19" r="1.6" />
      <circle cx="16.5" cy="19" r="1.6" />
    </svg>
  );
}

/** 放大镜 */
export function SearchIcon({ size = 22 }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </svg>
  );
}

/** 五角星 */
export function StarIcon({ size = 22, filled = false }: IconProps & { filled?: boolean }) {
  return (
    <svg {...base(size)} fill={filled ? "currentColor" : "none"}>
      <path d="M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.6 9.7l5.8-.8L12 3.6z" />
    </svg>
  );
}

/** 齿轮 */
export function GearIcon({ size = 22 }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="12" cy="12" r="3.2" />
      <path d="M19.4 13.5a7.6 7.6 0 0 0 0-3l1.8-1.2-2-3.4-2 .9a7.7 7.7 0 0 0-2.6-1.5L14.3 3h-4l-.3 2.3c-.9.3-1.8.8-2.6 1.5l-2-.9-2 3.4 1.8 1.2a7.6 7.6 0 0 0 0 3l-1.8 1.2 2 3.4 2-.9c.8.7 1.7 1.2 2.6 1.5l.3 2.3h4l.3-2.3c.9-.3 1.8-.8 2.6-1.5l2 .9 2-3.4-1.8-1.2z" />
    </svg>
  );
}

/** 定位靶心 */
export function LocateIcon({ size = 20 }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="12" cy="12" r="6.5" />
      <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3" />
    </svg>
  );
}

/** 返回箭头 */
export function BackIcon({ size = 20 }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M15 5l-7 7 7 7" />
    </svg>
  );
}
