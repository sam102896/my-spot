<template>
  <div ref="el" style="width: 100%; height: 520px" />
</template>

<script setup lang="ts">
import { createChart, ColorType, CrosshairMode, ISeriesApi, UTCTimestamp } from "lightweight-charts";
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import { toNumber } from "../../utils/format";

type Bar = { t: string; o: unknown; h: unknown; l: unknown; c: unknown; v: unknown };

const props = defineProps<{
  bars: Bar[];
  ma: number[];
}>();

const el = ref<HTMLElement | null>(null);
let chart: ReturnType<typeof createChart> | null = null;
let candleSeries: ISeriesApi<"Candlestick"> | null = null;
let volSeries: ISeriesApi<"Histogram"> | null = null;
let maSeries: Record<number, ISeriesApi<"Line">> = {};
let ro: ResizeObserver | null = null;

function toCandleData(b: Bar) {
  const time = Math.floor(Date.parse(b.t) / 1000) as UTCTimestamp;
  return {
    time,
    open: toNumber(b.o, 8),
    high: toNumber(b.h, 8),
    low: toNumber(b.l, 8),
    close: toNumber(b.c, 8)
  };
}

function toVolData(b: Bar) {
  const time = Math.floor(Date.parse(b.t) / 1000) as UTCTimestamp;
  return {
    time,
    value: toNumber(b.v, 8),
    color: "rgba(148,163,184,0.45)"
  };
}

function calcMa(period: number, bars: Bar[]) {
  const out: { time: UTCTimestamp; value: number }[] = [];
  for (let i = 0; i < bars.length; i++) {
    if (i + 1 < period) continue;
    let s = 0;
    let ok = true;
    for (let j = i + 1 - period; j <= i; j++) {
      const v = toNumber(bars[j].c, 8);
      if (!Number.isFinite(v)) {
        ok = false;
        break;
      }
      s += v;
    }
    if (!ok) continue;
    const avg = s / period;
    out.push({ time: Math.floor(Date.parse(bars[i].t) / 1000) as UTCTimestamp, value: avg });
  }
  return out;
}

function render() {
  if (!el.value) return;
  if (!chart) {
    chart = createChart(el.value, {
      layout: { background: { type: ColorType.Solid, color: "transparent" }, textColor: "rgba(229,231,235,0.9)" },
      grid: {
        vertLines: { color: "rgba(148,163,184,0.18)" },
        horzLines: { color: "rgba(148,163,184,0.18)" }
      },
      crosshair: { mode: CrosshairMode.Magnet },
      rightPriceScale: { borderColor: "rgba(148,163,184,0.25)" },
      timeScale: { borderColor: "rgba(148,163,184,0.25)" }
    });
    candleSeries = chart.addCandlestickSeries({
      upColor: "rgba(22,163,74,0.9)",
      downColor: "rgba(239,68,68,0.9)",
      borderUpColor: "rgba(22,163,74,0.9)",
      borderDownColor: "rgba(239,68,68,0.9)",
      wickUpColor: "rgba(22,163,74,0.9)",
      wickDownColor: "rgba(239,68,68,0.9)"
    });
    volSeries = chart.addHistogramSeries({ priceScaleId: "" });
    chart.priceScale("").applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });
    ro = new ResizeObserver(() => chart?.applyOptions({ width: el.value!.clientWidth, height: el.value!.clientHeight }));
    ro.observe(el.value);
  }
  const candles = props.bars.map(toCandleData);
  const vols = props.bars.map(toVolData);
  candleSeries!.setData(candles);
  volSeries!.setData(vols);
  // MA lines
  const maColors: Record<number, string> = { 5: "rgba(96,165,250,0.95)", 10: "rgba(245,158,11,0.95)", 20: "rgba(167,139,250,0.95)" };
  for (const p of Object.keys(maSeries).map(Number)) {
    if (!props.ma.includes(p)) {
      maSeries[p].setData([]);
      delete maSeries[p];
    }
  }
  for (const p of props.ma) {
    const data = calcMa(p, props.bars);
    if (!maSeries[p]) {
      maSeries[p] = chart.addLineSeries({ color: maColors[p] ?? "rgba(148,163,184,0.9)", lineWidth: 1 });
    }
    maSeries[p].setData(data);
  }
}

onMounted(() => {
  render();
});

onBeforeUnmount(() => {
  ro?.disconnect();
  ro = null;
  chart?.remove();
  chart = null;
  candleSeries = null;
  volSeries = null;
  maSeries = {};
});

watch(
  () => [props.bars, props.ma],
  () => render(),
  { deep: true }
);
</script>
