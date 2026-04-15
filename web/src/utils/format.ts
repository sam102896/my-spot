export const ATOMIC_DECIMALS = 8;

export function formatAtomic(v: unknown, decimals = 8): string {
  if (v === null || v === undefined) return "-";
  let bi: bigint;
  try {
    bi = typeof v === "bigint" ? v : BigInt(String(v));
  } catch {
    return "-";
  }
  const neg = bi < 0n;
  const abs = neg ? -bi : bi;
  const base = 10n ** BigInt(decimals);
  const whole = abs / base;
  const fracRaw = (abs % base).toString().padStart(decimals, "0");
  const frac = fracRaw.replace(/0+$/, "");
  const s = frac.length > 0 ? `${whole.toString()}.${frac}` : whole.toString();
  return neg ? `-${s}` : s;
}

export function toNumber(v: unknown, decimals = 8): number {
  const s = formatAtomic(v, decimals);
  if (s === "-") return NaN;
  const n = Number(s);
  return Number.isFinite(n) ? n : NaN;
}

export function atomicToNumber(v: unknown): number {
  return toNumber(v, ATOMIC_DECIMALS);
}

export function formatNumber(n: number, opts?: { decimals?: number; compact?: boolean }): string {
  if (!Number.isFinite(n)) return "-";
  const decimals = opts?.decimals ?? 2;
  if (opts?.compact) {
    const abs = Math.abs(n);
    const sign = n < 0 ? "-" : "";
    if (abs >= 1e9) return `${sign}${(abs / 1e9).toFixed(2)}B`;
    if (abs >= 1e6) return `${sign}${(abs / 1e6).toFixed(2)}M`;
    if (abs >= 1e3) return `${sign}${(abs / 1e3).toFixed(2)}K`;
  }
  return n.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals
  });
}

export function formatTimeHms(isoOrDate: string | Date): string {
  const d = typeof isoOrDate === "string" ? new Date(isoOrDate) : isoOrDate;
  if (!Number.isFinite(d.getTime())) return "-";
  const hh = d.getHours().toString().padStart(2, "0");
  const mm = d.getMinutes().toString().padStart(2, "0");
  const ss = d.getSeconds().toString().padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

