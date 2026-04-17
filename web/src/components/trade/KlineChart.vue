<template>
  <div ref="el" style="width: 100%; height: 520px" />
</template>

<script setup lang="ts">
import * as echarts from "echarts";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { atomicToNumber, formatTimeHms } from "../../utils/format";

type Bar = {
  t: string;
  o: unknown;
  h: unknown;
  l: unknown;
  c: unknown;
  v: unknown;
};

const props = defineProps<{
  bars: Bar[];
  ma: number[];
}>();

const el = ref<HTMLElement | null>(null);
let chart: echarts.ECharts | null = null;
let ro: ResizeObserver | null = null;

function normalizeValue(v: unknown): number {
  if (typeof v === "number" && Number.isFinite(v) && Math.abs(v) < 1_000_000) {
    return v;
  }
  return atomicToNumber(v);
}

const parsed = computed(() => {
  const times: string[] = [];
  const candles: [number, number, number, number][] = [];
  const vols: number[] = [];
  const closes: number[] = [];
  for (const b of props.bars) {
    times.push(formatTimeHms(b.t));
    const o = normalizeValue(b.o);
    const h = normalizeValue(b.h);
    const l = normalizeValue(b.l);
    const c = normalizeValue(b.c);
    const v = normalizeValue(b.v);
    candles.push([o, c, l, h]);
    vols.push(v);
    closes.push(c);
  }
  return { times, candles, vols, closes };
});

function calcMa(period: number, data: number[]): (number | null)[] {
  const out: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i + 1 < period) {
      out.push(null);
      continue;
    }
    let s = 0;
    for (let j = i + 1 - period; j <= i; j++) s += data[j];
    out.push(s / period);
  }
  return out;
}

function render() {
  if (!chart) return;
  const { times, candles, vols, closes } = parsed.value;
  const ma5 = props.ma.includes(5) ? calcMa(5, closes) : [];
  const ma10 = props.ma.includes(10) ? calcMa(10, closes) : [];
  const ma20 = props.ma.includes(20) ? calcMa(20, closes) : [];

  const option: echarts.EChartsOption = {
    animation: true,
    backgroundColor: "transparent",
    textStyle: { color: "rgba(229,231,235,0.85)" },
    grid: [
      { left: 10, right: 10, top: 16, height: 340 },
      { left: 10, right: 10, top: 372, height: 110 }
    ],
    axisPointer: {
      link: [{ xAxisIndex: [0, 1] }],
      label: { backgroundColor: "rgba(15,23,42,0.9)" }
    },
    xAxis: [
      {
        type: "category",
        data: times,
        boundaryGap: false,
        axisLine: { lineStyle: { color: "rgba(148,163,184,0.2)" } },
        axisLabel: { color: "rgba(229,231,235,0.6)" },
        splitLine: { show: false }
      },
      {
        type: "category",
        gridIndex: 1,
        data: times,
        boundaryGap: false,
        axisLine: { lineStyle: { color: "rgba(148,163,184,0.2)" } },
        axisLabel: { show: false },
        splitLine: { show: false }
      }
    ],
    yAxis: [
      {
        scale: true,
        axisLine: { lineStyle: { color: "rgba(148,163,184,0.2)" } },
        axisLabel: { color: "rgba(229,231,235,0.6)" },
        splitLine: { lineStyle: { color: "rgba(148,163,184,0.08)" } }
      },
      {
        gridIndex: 1,
        scale: true,
        axisLine: { lineStyle: { color: "rgba(148,163,184,0.2)" } },
        axisLabel: { color: "rgba(229,231,235,0.6)" },
        splitLine: { lineStyle: { color: "rgba(148,163,184,0.06)" } }
      }
    ],
    dataZoom: [
      { type: "inside", xAxisIndex: [0, 1], start: 40, end: 100 },
      { type: "slider", xAxisIndex: [0, 1], bottom: 0, height: 18, borderColor: "transparent" }
    ],
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross" }
    },
    series: [
      {
        name: "K",
        type: "candlestick",
        data: candles,
        itemStyle: {
          color: "rgba(22,163,74,0.85)",
          color0: "rgba(239,68,68,0.85)",
          borderColor: "rgba(22,163,74,0.85)",
          borderColor0: "rgba(239,68,68,0.85)"
        }
      },
      ...(props.ma.includes(5)
        ? [
            {
              name: "MA5",
              type: "line",
              data: ma5,
              symbol: "none",
              lineStyle: { width: 1, color: "rgba(96,165,250,0.9)" }
            } as echarts.SeriesOption
          ]
        : []),
      ...(props.ma.includes(10)
        ? [
            {
              name: "MA10",
              type: "line",
              data: ma10,
              symbol: "none",
              lineStyle: { width: 1, color: "rgba(245,158,11,0.9)" }
            } as echarts.SeriesOption
          ]
        : []),
      ...(props.ma.includes(20)
        ? [
            {
              name: "MA20",
              type: "line",
              data: ma20,
              symbol: "none",
              lineStyle: { width: 1, color: "rgba(167,139,250,0.9)" }
            } as echarts.SeriesOption
          ]
        : []),
      {
        name: "Volume",
        type: "bar",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: vols,
        itemStyle: { color: "rgba(148,163,184,0.35)" }
      }
    ]
  };
  chart.setOption(option, true);
}

onMounted(() => {
  if (!el.value) return;
  chart = echarts.init(el.value);
  ro = new ResizeObserver(() => chart?.resize());
  ro.observe(el.value);
  render();
});

onBeforeUnmount(() => {
  ro?.disconnect();
  ro = null;
  chart?.dispose();
  chart = null;
});

watch(
  () => [props.bars, props.ma],
  () => render(),
  { deep: true }
);
</script>
