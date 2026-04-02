<template>
  <div class="panel">
    <div class="panel-header">
      <div class="panel-title">最近成交</div>
      <div class="muted mono" style="font-size: 12px">{{ pair }}</div>
    </div>
    <div class="panel-body" style="padding: 0">
      <div class="scroll" style="max-height: 320px">
        <table class="table">
          <thead>
            <tr>
              <th style="text-align: right">价格</th>
              <th style="text-align: right">数量</th>
              <th style="text-align: right">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in rows"
              :key="t.key"
              :class="t.flashClass"
              style="transition: background 240ms ease"
            >
              <td class="mono" :style="{ textAlign: 'right', color: t.color }">{{ t.price }}</td>
              <td class="mono" style="text-align: right">{{ t.qty }}</td>
              <td class="mono muted" style="text-align: right">{{ t.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { formatNumber, formatTimeHms, toNumber } from "../../utils/format";

type Trade = { price: unknown; qty: unknown; createdAt: string };

const props = defineProps<{
  pair: string;
  trades: Trade[];
  priceDecimals?: number;
  qtyDecimals?: number;
}>();

const flashKeys = ref(new Set<string>());
let timer: number | null = null;

function bumpFlash(keys: string[]) {
  flashKeys.value = new Set(keys);
  if (timer) window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    flashKeys.value.clear();
  }, 1200);
}

const lastTopKey = ref<string>("");

const rows = computed(() => {
  const priceDecimals = props.priceDecimals ?? 8;
  const qtyDecimals = props.qtyDecimals ?? 8;
  const out: { key: string; price: string; qty: string; time: string; color: string; flashClass: string }[] = [];

  let prevPx = NaN;
  for (const t of props.trades ?? []) {
    const pn = toNumber(t.price, priceDecimals);
    const qn = toNumber(t.qty, qtyDecimals);
    const px = formatNumber(pn, { decimals: 2 });
    const qty = formatNumber(qn, { decimals: 6 });
    const time = formatTimeHms(t.createdAt);

    let color = "var(--muted)";
    if (Number.isFinite(prevPx) && Number.isFinite(pn)) {
      if (pn > prevPx) color = "var(--buy)";
      else if (pn < prevPx) color = "var(--sell)";
      else color = "var(--muted)";
    }
    prevPx = pn;

    const key = `${t.createdAt}:${String(t.price)}:${String(t.qty)}`;
    const flashClass = flashKeys.value.has(key) ? "flash-buy" : "";
    out.push({ key, price: px, qty, time, color, flashClass });
  }
  return out;
});

watch(
  () => props.trades,
  () => {
    const top = props.trades?.[0];
    if (!top) return;
    const key = `${top.createdAt}:${String(top.price)}:${String(top.qty)}`;
    if (lastTopKey.value && key !== lastTopKey.value) bumpFlash([key]);
    lastTopKey.value = key;
  },
  { deep: true }
);

onBeforeUnmount(() => {
  if (timer) window.clearTimeout(timer);
});
</script>

