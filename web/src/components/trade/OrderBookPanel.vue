<template>
  <div class="panel">
    <div class="panel-header">
      <div class="panel-title">
        <span>{{ title }}</span>
        <span class="badge" style="gap: 6px">
          <span :class="['dot', wsDot]" />
          <span class="muted mono">{{ wsText }}</span>
        </span>
      </div>
      <div style="display: flex; gap: 8px; align-items: center">
        <button class="btn btn-ghost" @click="emit('refresh')" :disabled="loading">刷新</button>
      </div>
    </div>

    <div class="panel-body" style="padding: 0">
      <div class="grid orderbook-body" style="grid-template-columns: 1fr 1fr; gap: 0">
        <div class="scroll" style="height: 100%">
          <table class="table">
            <thead>
              <tr>
                <th style="text-align: right">价格</th>
                <th style="text-align: right">数量</th>
                <th style="text-align: right">累计</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in bidsRows"
                :key="'b:' + r.priceKey"
                :class="r.flashClass"
                @click="emit('selectPrice', r.priceDisplay)"
                style="cursor: pointer; position: relative"
              >
                <td class="mono" style="text-align: right; color: var(--buy); position: relative">
                  <div class="depth-bar buy" :style="{ width: r.pct + '%' }" />
                  <span style="position: relative; z-index: 1">{{ r.priceDisplay }}</span>
                </td>
                <td class="mono" style="text-align: right">{{ r.qtyDisplay }}</td>
                <td class="mono" style="text-align: right; color: var(--muted)">{{ r.cumDisplay }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="scroll" style="height: 100%">
          <table class="table">
            <thead>
              <tr>
                <th style="text-align: right">价格</th>
                <th style="text-align: right">数量</th>
                <th style="text-align: right">累计</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in asksRows"
                :key="'a:' + r.priceKey"
                :class="r.flashClass"
                @click="emit('selectPrice', r.priceDisplay)"
                style="cursor: pointer; position: relative"
              >
                <td class="mono" style="text-align: right; color: var(--sell); position: relative">
                  <div class="depth-bar sell" :style="{ width: r.pct + '%' }" />
                  <span style="position: relative; z-index: 1">{{ r.priceDisplay }}</span>
                </td>
                <td class="mono" style="text-align: right">{{ r.qtyDisplay }}</td>
                <td class="mono" style="text-align: right; color: var(--muted)">{{ r.cumDisplay }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { atomicToNumber, formatNumber } from "../../utils/format";

type Level = { price: unknown; qty: unknown };
type WsStatus = "connecting" | "open" | "closed" | "error" | "";

const props = defineProps<{
  title: string;
  bids: Level[];
  asks: Level[];
  wsStatus: WsStatus;
  loading: boolean;
  priceDecimals?: number;
  qtyDecimals?: number;
  maxBodyHeight?: string;
}>();

const emit = defineEmits<{
  (e: "selectPrice", price: string): void;
  (e: "refresh"): void;
}>();

type Row = {
  priceKey: string;
  priceDisplay: string;
  qtyDisplay: string;
  cumDisplay: string;
  pct: number;
  flashClass: string;
};

const flashMap = ref(new Map<string, string>());
let timer: number | null = null;

function clearFlashLater() {
  if (timer) window.clearTimeout(timer);
  timer = window.setTimeout(() => flashMap.value.clear(), 900);
}

function markFlash(side: "b" | "a", priceKey: string, kind: "buy" | "sell") {
  flashMap.value.set(`${side}:${priceKey}`, kind === "buy" ? "flash-buy" : "flash-sell");
  clearFlashLater();
}

function keyOfPrice(p: unknown): string {
  return String(p);
}

function makeRows(side: "b" | "a", levels: Level[], kind: "buy" | "sell"): Row[] {
  const priceDecimals = props.priceDecimals ?? 8;
  const qtyDecimals = props.qtyDecimals ?? 8;

  const cumArr: number[] = [];
  let cum = 0;
  for (const l of levels) {
    const qn = atomicToNumber(l.qty);
    cum += Number.isFinite(qn) ? qn : 0;
    cumArr.push(cum);
  }
  const maxCum = cumArr.length > 0 ? Math.max(...cumArr) : 1;

  return levels.map((l, idx) => {
    const priceKey = keyOfPrice(l.price);
    const flashClass = flashMap.value.get(`${side}:${priceKey}`) ?? "";
    const priceDisplay = formatNumber(atomicToNumber(l.price), { decimals: priceDecimals });
    const qtyDisplay = formatNumber(atomicToNumber(l.qty), { decimals: qtyDecimals });
    const cumDisplay = formatNumber(cumArr[idx], { decimals: qtyDecimals, compact: true });
    const pct = Math.max(0, Math.min(100, (cumArr[idx] / maxCum) * 100));
    return { priceKey, priceDisplay, qtyDisplay, cumDisplay, pct, flashClass };
  });
}

const bidsRows = computed(() => makeRows("b", props.bids ?? [], "buy"));
const asksRows = computed(() => makeRows("a", props.asks ?? [], "sell"));

const wsDot = computed(() => {
  if (props.wsStatus === "open") return "ok";
  if (props.wsStatus === "connecting") return "warn";
  if (props.wsStatus === "error") return "bad";
  if (props.wsStatus === "closed") return "bad";
  return "";
});

const wsText = computed(() => {
  const s = props.wsStatus;
  if (!s) return "offline";
  return s;
});

const prev = ref<{ b: Map<string, string>; a: Map<string, string> }>({ b: new Map(), a: new Map() });

watch(
  () => [props.bids, props.asks],
  () => {
    const nextB = new Map<string, string>();
    for (const l of props.bids ?? []) nextB.set(keyOfPrice(l.price), String(l.qty));
    const nextA = new Map<string, string>();
    for (const l of props.asks ?? []) nextA.set(keyOfPrice(l.price), String(l.qty));

    for (const [k, v] of nextB) {
      const pv = prev.value.b.get(k);
      if (pv !== undefined && pv !== v) markFlash("b", k, "buy");
    }
    for (const [k, v] of nextA) {
      const pv = prev.value.a.get(k);
      if (pv !== undefined && pv !== v) markFlash("a", k, "sell");
    }

    prev.value = { b: nextB, a: nextA };
  },
  { deep: true }
);

onBeforeUnmount(() => {
  if (timer) window.clearTimeout(timer);
});
</script>

<style scoped>
.orderbook-body {
  height: v-bind("props.maxBodyHeight ?? '320px'");
}

.depth-bar {
  position: absolute;
  inset: 0;
  left: auto;
  right: 0;
  height: 100%;
  opacity: 0.55;
  transition: width 220ms ease;
}

.depth-bar.buy {
  background: linear-gradient(90deg, transparent, rgba(22, 163, 74, 0.18));
}

.depth-bar.sell {
  background: linear-gradient(90deg, transparent, rgba(239, 68, 68, 0.18));
}
</style>
