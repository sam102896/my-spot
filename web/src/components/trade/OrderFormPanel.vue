<template>
  <div class="panel">
    <div class="panel-header">
      <div class="panel-title">下单</div>
      <div class="muted mono" style="font-size: 12px">{{ pair }}</div>
    </div>
    <div class="panel-body">
      <div style="display: grid; gap: 10px">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px">
          <button
            class="btn btn-sm"
            :class="side === 'BUY' ? 'btn-buy' : 'btn-ghost'"
            @click="emit('update:side', 'BUY')"
            :disabled="disabled"
          >
            买入
          </button>
          <button
            class="btn btn-sm"
            :class="side === 'SELL' ? 'btn-sell' : 'btn-ghost'"
            @click="emit('update:side', 'SELL')"
            :disabled="disabled"
          >
            卖出
          </button>
        </div>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px">
          <label style="display: grid; gap: 6px">
            <div class="muted">类型</div>
            <select class="select" :value="type" @change="onType($event)" :disabled="disabled">
              <option value="LIMIT">限价</option>
              <option value="MARKET">市价</option>
            </select>
          </label>
          <div class="badge" style="justify-content: space-between">
            <span class="muted">参考价</span>
            <span class="mono">{{ refPriceText }}</span>
          </div>
        </div>

        <label v-if="type === 'LIMIT'" style="display: grid; gap: 6px">
          <div class="muted">价格</div>
          <input class="input mono" :value="price" @input="emit('update:price', ($event.target as any).value)" />
        </label>

        <label style="display: grid; gap: 6px">
          <div class="muted">数量</div>
          <input class="input mono" :value="qty" @input="emit('update:qty', ($event.target as any).value)" />
        </label>

        <div style="display: flex; gap: 8px; flex-wrap: wrap">
          <button class="btn btn-sm" @click="fillPct(0.25)" :disabled="disabled">25%</button>
          <button class="btn btn-sm" @click="fillPct(0.5)" :disabled="disabled">50%</button>
          <button class="btn btn-sm" @click="fillPct(0.75)" :disabled="disabled">75%</button>
          <button class="btn btn-sm" @click="fillPct(1)" :disabled="disabled">100%</button>
          <button class="btn btn-ghost btn-sm" @click="fillMax" :disabled="disabled">最大</button>
        </div>

        <div class="badge" style="justify-content: space-between">
          <span class="muted">可用</span>
          <span class="mono">{{ availableText }}</span>
        </div>

        <button
          class="btn btn-sm"
          :class="side === 'BUY' ? 'btn-buy' : 'btn-sell'"
          @click="emit('submit')"
          :disabled="disabled"
          style="padding: 11px 12px; font-weight: 700"
        >
          {{ disabled ? "下单中…" : side === "BUY" ? "买入" : "卖出" }}
        </button>

        <div v-if="message" class="badge" style="border-color: rgba(22, 163, 74, 0.28)">
          <span class="dot ok" />
          <span class="mono">{{ message }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { formatNumber } from "../../utils/format";

const props = defineProps<{
  pair: string;
  baseAsset: string;
  quoteAsset: string;
  side: "BUY" | "SELL";
  type: "LIMIT" | "MARKET";
  price: string;
  qty: string;
  disabled: boolean;
  message: string;
  bestBid?: number;
  bestAsk?: number;
  availableBase?: number;
  availableQuote?: number;
}>();

const emit = defineEmits<{
  (e: "update:side", v: "BUY" | "SELL"): void;
  (e: "update:type", v: "LIMIT" | "MARKET"): void;
  (e: "update:price", v: string): void;
  (e: "update:qty", v: string): void;
  (e: "submit"): void;
}>();

function onType(ev: Event) {
  const v = (ev.target as HTMLSelectElement).value as "LIMIT" | "MARKET";
  emit("update:type", v);
}

const refPrice = computed(() => {
  if (props.side === "BUY") return props.bestAsk ?? props.bestBid ?? NaN;
  return props.bestBid ?? props.bestAsk ?? NaN;
});

const refPriceText = computed(() => {
  const n = refPrice.value;
  return Number.isFinite(n) ? formatNumber(n, { decimals: 2 }) : "-";
});

const availableText = computed(() => {
  if (props.side === "BUY") {
    const q = props.availableQuote ?? NaN;
    return Number.isFinite(q) ? `${formatNumber(q, { decimals: 6 })} ${props.quoteAsset}` : "-";
  }
  const b = props.availableBase ?? NaN;
  return Number.isFinite(b) ? `${formatNumber(b, { decimals: 6 })} ${props.baseAsset}` : "-";
});

function usedPrice(): number {
  if (props.type === "LIMIT") {
    const n = Number(props.price);
    return Number.isFinite(n) && n > 0 ? n : refPrice.value;
  }
  return refPrice.value;
}

function fillPct(pct: number) {
  if (props.side === "BUY") {
    const quote = props.availableQuote ?? NaN;
    const px = usedPrice();
    if (!Number.isFinite(quote) || !Number.isFinite(px) || px <= 0) return;
    const q = (quote * pct) / px;
    emit("update:qty", q.toFixed(6));
    return;
  }
  const base = props.availableBase ?? NaN;
  if (!Number.isFinite(base)) return;
  emit("update:qty", (base * pct).toFixed(6));
}

function fillMax() {
  fillPct(1);
}
</script>
