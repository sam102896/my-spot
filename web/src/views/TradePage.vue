<template>
  <div class="trade-page">
    <div class="trade-terminal">
      <div class="trade-topbar">
        <div class="trade-topbar-left">
          <div class="trade-pair-box">
            <div class="trade-pair-main">
              <select class="trade-pair-select" v-model="pair" @change="reloadAll">
                <option v-for="p in pairs" :key="p.symbol" :value="p.symbol">{{ p.symbol }}</option>
              </select>
              <div class="trade-last">
                <div class="trade-last-price mono" :style="{ color: changeColor }">{{ lastPriceText }}</div>
                <div class="trade-last-change mono" :style="{ color: changeColor }">{{ changeText }}</div>
              </div>
            </div>
            <div class="trade-stats-row">
              <div class="trade-stat">
                <span class="trade-stat-label">24H 高</span>
                <span class="trade-stat-value mono">{{ statsHighText }}</span>
              </div>
              <div class="trade-stat">
                <span class="trade-stat-label">24H 低</span>
                <span class="trade-stat-value mono">{{ statsLowText }}</span>
              </div>
              <div class="trade-stat">
                <span class="trade-stat-label">24H 量</span>
                <span class="trade-stat-value mono">{{ statsVolText }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="trade-topbar-right">
          <span v-if="!auth.token" class="badge" style="border-color: rgba(239, 68, 68, 0.25)">
            <span class="dot bad" />
            <span>请先登录后交易</span>
          </span>
          <span class="badge">
            <span :class="['dot', wsDot]" />
            <span class="mono muted">WS: {{ wsStatus || "offline" }}</span>
          </span>
          <button class="btn btn-primary btn-sm" @click="reloadAll" :disabled="loading">刷新</button>
        </div>
      </div>

      <div class="trade-main-grid">
        <section class="trade-chart-shell panel">
          <div class="trade-chart-header">
            <div class="trade-chart-tabs">
              <button class="trade-tab-btn" :class="{ active: tf === '1m' }" @click="tf = '1m'">1m</button>
              <button class="trade-tab-btn" :class="{ active: tf === '5m' }" @click="tf = '5m'">5m</button>
              <button class="trade-tab-btn" :class="{ active: tf === '15m' }" @click="tf = '15m'">15m</button>
              <button class="trade-tab-btn" :class="{ active: tf === '1h' }" @click="tf = '1h'">1H</button>
            </div>
            <div class="trade-chart-actions">
              <div class="trade-indicator-group">
                <span class="trade-indicator-label">MA</span>
                <button class="trade-chip-btn" :class="{ active: showMa5 }" @click="showMa5 = !showMa5">5</button>
                <button class="trade-chip-btn" :class="{ active: showMa10 }" @click="showMa10 = !showMa10">10</button>
                <button class="trade-chip-btn" :class="{ active: showMa20 }" @click="showMa20 = !showMa20">20</button>
              </div>
            </div>
          </div>
          <div class="trade-chart-body">
            <KlineChart v-if="chartAlive && chartBars.length > 0" :bars="chartBars" :ma="maList" />
          </div>
        </section>

        <section class="trade-market-shell panel">
          <div class="trade-market-header">
            <div class="trade-chart-tabs">
              <button class="trade-tab-btn" :class="{ active: marketTab === 'book' }" @click="marketTab = 'book'">
                盘口
              </button>
              <button class="trade-tab-btn" :class="{ active: marketTab === 'trades' }" @click="marketTab = 'trades'">
                最近成交
              </button>
            </div>
            <div v-if="marketTab === 'book'" class="trade-market-extra">
              <span :class="['dot', wsDot]" />
              <span class="mono muted">WS: {{ wsStatus || "offline" }}</span>
            </div>
            <div v-else class="trade-market-extra">
              <span class="mono muted">{{ pair }}</span>
            </div>
          </div>
          <div class="trade-market-body">
            <OrderBookPanel
              v-if="marketTab === 'book'"
              title="盘口"
              :bids="book.bids"
              :asks="book.asks"
              :ws-status="wsStatus"
              :loading="loading"
              :price-decimals="pairMeta?.priceDecimals ?? 8"
              :qty-decimals="pairMeta?.qtyDecimals ?? 8"
              max-body-height="100%"
              @refresh="loadMarket"
              @select-price="onSelectPrice"
            />
            <TradesPanel
              v-else
              :pair="pair"
              :trades="trades"
              :price-decimals="pairMeta?.priceDecimals ?? 8"
              :qty-decimals="pairMeta?.qtyDecimals ?? 8"
              max-body-height="100%"
            />
          </div>
        </section>

        <section class="trade-order-shell">
          <OrderFormPanel
            :pair="pair"
            :base-asset="pairMeta?.base ?? '-'"
            :quote-asset="pairMeta?.quote ?? '-'"
            v-model:side="order.side"
            v-model:type="order.type"
            v-model:price="order.price"
            v-model:qty="order.qty"
            :best-bid="bestBid"
            :best-ask="bestAsk"
            :available-base="availableBase"
            :available-quote="availableQuote"
            :disabled="loading || !auth.token"
            :message="order.msg"
            @submit="placeOrder"
          />
          <div class="panel trade-balance-card">
            <div class="panel-header">
              <div class="panel-title">仓位概览</div>
            </div>
            <div class="panel-body">
              <div class="trade-balance-row">
                <span class="muted">{{ pairMeta?.base ?? "-" }} 可用</span>
                <span class="mono">{{ availableBaseText }}</span>
              </div>
              <div class="trade-balance-row">
                <span class="muted">{{ pairMeta?.quote ?? "-" }} 可用</span>
                <span class="mono">{{ availableQuoteText }}</span>
              </div>
              <div class="trade-balance-row">
                <span class="muted">统计周期</span>
                <span class="mono">{{ statsPeriodText }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="trade-bottom">
        <OrdersPanel
          :open-orders="openOrders"
          :history-orders="historyOrders"
          :loading="loading"
          :price-decimals="pairMeta?.priceDecimals ?? 8"
          :qty-decimals="pairMeta?.qtyDecimals ?? 8"
          @cancel="cancel"
        />
      </div>

      <div v-if="err" class="badge trade-error" style="border-color: rgba(239, 68, 68, 0.25)">
        <span class="dot bad" />
        <span>{{ err }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useToastStore } from "../stores/toast";
import { atomicToNumber, formatNumber, toNumber } from "../utils/format";
import KlineChart from "../components/trade/KlineChart.vue";
import OrderBookPanel from "../components/trade/OrderBookPanel.vue";
import OrderFormPanel from "../components/trade/OrderFormPanel.vue";
import OrdersPanel from "../components/trade/OrdersPanel.vue";
import TradesPanel from "../components/trade/TradesPanel.vue";

const auth = useAuthStore();
const toast = useToastStore();
const loading = ref(false);
const err = ref("");

type PairMeta = {
  symbol: string;
  base: string;
  quote: string;
  minQty: number;
  minNotional: number;
  feeBps: number;
  priceDecimals: number;
  qtyDecimals: number;
};

const pairs = ref<PairMeta[]>([]);
const pair = ref("BTCUSDT");
const pairMeta = computed(() => pairs.value.find((p) => p.symbol === pair.value));

const book = reactive({ bids: [] as any[], asks: [] as any[] });
const trades = ref<any[]>([]);
const kline = ref<any[]>([]);
const openOrders = ref<any[]>([]);
const historyOrders = ref<any[]>([]);

const bestBid = ref<number | undefined>(undefined);
const bestAsk = ref<number | undefined>(undefined);

const availableBase = ref<number | undefined>(undefined);
const availableQuote = ref<number | undefined>(undefined);

const tf = ref<"1m" | "5m" | "15m" | "1h">("1m");
const marketTab = ref<"book" | "trades">("book");
const chartAlive = ref(true);
const showMa5 = ref(true);
const showMa10 = ref(true);
const showMa20 = ref(true);
const maList = computed(() => {
  const out: number[] = [];
  if (showMa5.value) out.push(5);
  if (showMa10.value) out.push(10);
  if (showMa20.value) out.push(20);
  return out;
});

const order = reactive({
  side: "BUY" as "BUY" | "SELL",
  type: "LIMIT" as "LIMIT" | "MARKET",
  price: "65000",
  qty: "0.001",
  msg: ""
});

const wsStatus = ref<"" | "connecting" | "open" | "closed" | "error">("");
let ws: WebSocket | null = null;
let pageDisposed = false;

function apiErr(e: any): string {
  return e?.response?.data?.message ?? e?.message ?? "请求失败";
}

async function loadPairs() {
  const res = await http.get("/api/public/market/pairs");
  if (pageDisposed) return;
  pairs.value = res.data;
  if (!pairs.value.find((p) => p.symbol === pair.value) && pairs.value.length > 0) {
    pair.value = pairs.value[0].symbol;
  }
}

async function loadMarket() {
  const [bRes, tRes, kRes] = await Promise.all([
    http.get(`/api/public/market/orderbook?pair=${pair.value}`),
    http.get(`/api/public/market/trades?pair=${pair.value}&limit=20`),
    http.get(`/api/public/market/kline?pair=${pair.value}&limit=30`)
  ]);
  if (pageDisposed) return;
  book.bids = bRes.data.bids;
  book.asks = bRes.data.asks;
  trades.value = tRes.data;
  kline.value = kRes.data;

  const pd = pairMeta.value?.priceDecimals ?? 8;
  bestBid.value = book.bids?.[0]?.price != null ? atomicToNumber(book.bids[0].price) : undefined;
  bestAsk.value = book.asks?.[0]?.price != null ? atomicToNumber(book.asks[0].price) : undefined;
}

async function loadOrders() {
  if (!auth.token) {
    openOrders.value = [];
    historyOrders.value = [];
    return;
  }
  const [oRes, hRes] = await Promise.all([
    http.get("/api/trade/open-orders?limit=50"),
    http.get("/api/trade/orders?limit=100")
  ]);
  if (pageDisposed) return;
  openOrders.value = Array.isArray(oRes.data) ? oRes.data : [];
  historyOrders.value = Array.isArray(hRes.data) ? hRes.data : [];
}

async function loadWallets() {
  if (!auth.token) return;
  const res = await http.get("/api/account/wallets");
  if (pageDisposed) return;
  const items: any[] = res.data;
  const base = pairMeta.value?.base;
  const quote = pairMeta.value?.quote;
  if (!base || !quote) return;
  const baseW = items.find((w) => w.asset === base);
  const quoteW = items.find((w) => w.asset === quote);
  availableBase.value = baseW ? toNumber(baseW.available, 8) : undefined;
  availableQuote.value = quoteW ? toNumber(quoteW.available, 8) : undefined;
}

async function reloadAll() {
  if (pageDisposed) return;
  err.value = "";
  loading.value = true;
  try {
    await loadPairs();
    await loadMarket();
    await loadOrders();
    await loadWallets();
    connectWs();
  } catch (e: any) {
    if (pageDisposed) return;
    err.value = apiErr(e);
    toast.push("Trade", err.value);
  } finally {
    loading.value = false;
  }
}

async function placeOrder() {
  if (pageDisposed) return;
  order.msg = "";
  err.value = "";
  loading.value = true;
  try {
    const idemKey = crypto.randomUUID();
    const res = await http.post(
      "/api/trade/order",
      {
        pair: pair.value,
        side: order.side,
        type: order.type,
        price: order.type === "LIMIT" ? order.price : undefined,
        qty: order.qty
      },
      { headers: { "X-Idempotency-Key": idemKey } }
    );
    order.msg = `订单已提交：${res.data.id}（${res.data.status}）`;
    await loadOrders();
    await loadMarket();
    await loadWallets();
  } catch (e: any) {
    if (pageDisposed) return;
    err.value = apiErr(e);
    toast.push("Order", err.value);
  } finally {
    loading.value = false;
  }
}

async function cancel(id: string) {
  if (pageDisposed) return;
  err.value = "";
  loading.value = true;
  try {
    await http.post(`/api/trade/order/${id}/cancel`);
    await loadOrders();
    await loadMarket();
    await loadWallets();
  } catch (e: any) {
    if (pageDisposed) return;
    err.value = apiErr(e);
    toast.push("Cancel", err.value);
  } finally {
    loading.value = false;
  }
}

function disconnectWs() {
  if (!ws) return;
  ws.onopen = null;
  ws.onclose = null;
  ws.onerror = null;
  ws.onmessage = null;
  try {
    ws.close();
  } catch {
    // 忽略路由切换时底层 ws 关闭异常。
  }
  ws = null;
}

function connectWs() {
  if (pageDisposed) return;
  disconnectWs();
  wsStatus.value = "connecting";
  ws = new WebSocket(`ws://localhost:3001/api/public/ws/market?pair=${pair.value}`);
  ws.onopen = () => (wsStatus.value = "open");
  ws.onclose = () => (wsStatus.value = "closed");
  ws.onerror = () => (wsStatus.value = "error");
  ws.onmessage = (ev) => {
    if (pageDisposed) return;
    try {
      const payload = JSON.parse(ev.data);
      if (payload?.type === "trade") {
        const t = {
          price: payload.price,
          qty: payload.qty,
          createdAt: payload.createdAt
        };
        trades.value = [t, ...trades.value].slice(0, 200);
        upsertBarFromTrade(t);
        updateBestPrices();
      } else if (payload?.type === "book") {
        // 仅刷新盘口，避免无谓的全量刷新
        http
          .get(`/api/public/market/orderbook?pair=${pair.value}`)
          .then((res) => {
            if (pageDisposed) return;
            book.bids = res.data.bids;
            book.asks = res.data.asks;
            updateBestPrices();
          })
          .catch(() => {});
      }
    } catch {
      // 回退到较保守的刷新策略
      loadMarket().catch(() => {});
      loadOrders().catch(() => {});
      loadWallets().catch(() => {});
    }
  };
}

function onSelectPrice(p: string) {
  order.type = "LIMIT";
  order.price = p;
}

const wsDot = computed(() => {
  if (wsStatus.value === "open") return "ok";
  if (wsStatus.value === "connecting") return "warn";
  if (wsStatus.value === "error") return "bad";
  if (wsStatus.value === "closed") return "bad";
  return "";
});

const lastPrice = computed(() => {
  const pd = pairMeta.value?.priceDecimals ?? 8;
  const top = trades.value?.[0];
  if (top?.price == null) return NaN;
  return atomicToNumber(top.price);
});

const lastPriceText = computed(() => formatNumber(lastPrice.value, { decimals: pairMeta.value?.priceDecimals ?? 2 }));

const change = computed(() => {
  const bars = kline.value;
  const pd = pairMeta.value?.priceDecimals ?? 8;
  if (!bars || bars.length < 2) return { abs: NaN, pct: NaN };
  const first = atomicToNumber(bars[0].o);
  const last = atomicToNumber(bars[bars.length - 1].c);
  if (!Number.isFinite(first) || !Number.isFinite(last) || first === 0) return { abs: NaN, pct: NaN };
  return { abs: last - first, pct: ((last - first) / first) * 100 };
});

const changeColor = computed(() => {
  if (!Number.isFinite(change.value.abs)) return "var(--muted)";
  return change.value.abs >= 0 ? "var(--buy)" : "var(--sell)";
});

const changeText = computed(() => {
  if (!Number.isFinite(change.value.abs)) return "-";
  const s = change.value.abs >= 0 ? "+" : "";
  return `${s}${formatNumber(change.value.abs, { decimals: 2 })} (${s}${formatNumber(change.value.pct, { decimals: 2 })}%)`;
});

type Bar = { t: string; o: unknown; h: unknown; l: unknown; c: unknown; v: unknown };

const stats = computed(() => {
  const bars = (kline.value ?? []) as Bar[];
  const pd = pairMeta.value?.priceDecimals ?? 8;
  const qd = pairMeta.value?.qtyDecimals ?? 8;
  if (!bars.length) {
    return { open: NaN, close: NaN, high: NaN, low: NaN, vol: NaN, minutes: 0 };
  }
  let high = -Infinity;
  let low = Infinity;
  let vol = 0;
  for (const b of bars) {
    const h = atomicToNumber(b.h);
    const l = atomicToNumber(b.l);
    const v = atomicToNumber(b.v);
    if (Number.isFinite(h)) high = Math.max(high, h);
    if (Number.isFinite(l)) low = Math.min(low, l);
    if (Number.isFinite(v)) vol += v;
  }
  const open = atomicToNumber(bars[0].o);
  const close = atomicToNumber(bars[bars.length - 1].c);
  return { open, close, high, low, vol, minutes: bars.length };
});

const statsPeriodText = computed(() => {
  const m = stats.value.minutes;
  if (m >= 1440) return "24h";
  if (m >= 60) return `近 ${Math.floor(m / 60)}h`;
  return `近 ${m}m`;
});

const statsHighText = computed(() => formatNumber(stats.value.high, { decimals: pairMeta.value?.priceDecimals ?? 2 }));
const statsLowText = computed(() => formatNumber(stats.value.low, { decimals: pairMeta.value?.priceDecimals ?? 2 }));
const statsVolText = computed(() => formatNumber(stats.value.vol, { decimals: pairMeta.value?.qtyDecimals ?? 2, compact: true }));
const availableBaseText = computed(() =>
  Number.isFinite(availableBase.value ?? NaN)
    ? `${formatNumber(availableBase.value ?? 0, { decimals: pairMeta.value?.qtyDecimals ?? 6 })} ${pairMeta.value?.base ?? ""}`.trim()
    : "-"
);
const availableQuoteText = computed(() =>
  Number.isFinite(availableQuote.value ?? NaN)
    ? `${formatNumber(availableQuote.value ?? 0, { decimals: pairMeta.value?.priceDecimals ?? 2 })} ${pairMeta.value?.quote ?? ""}`.trim()
    : "-"
);

function aggregateBars(bars: Bar[], minutes: number): Bar[] {
  if (minutes <= 1) return bars;
  const intervalSec = minutes * 60;
  const map = new Map<number, { t: string; o: number; h: number; l: number; c: number; v: number }>();
  const pd = pairMeta.value?.priceDecimals ?? 8;
  const qd = pairMeta.value?.qtyDecimals ?? 8;
  for (const b of bars) {
    const ts = Date.parse(b.t);
    if (!Number.isFinite(ts)) continue;
    const sec = Math.floor(ts / 1000);
    const bucket = sec - (sec % intervalSec);
    const o = atomicToNumber(b.o);
    const h = atomicToNumber(b.h);
    const l = atomicToNumber(b.l);
    const c = atomicToNumber(b.c);
    const v = atomicToNumber(b.v);
    const key = bucket;
    const existing = map.get(key);
    if (!existing) {
      map.set(key, { t: new Date(bucket * 1000).toISOString(), o, h, l, c, v });
    } else {
      existing.h = Math.max(existing.h, h);
      existing.l = Math.min(existing.l, l);
      existing.c = c;
      existing.v += v;
    }
  }
  return [...map.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([, x]) => ({ t: x.t, o: x.o, h: x.h, l: x.l, c: x.c, v: x.v }));
}

const chartBars = computed(() => {
  const bars = (kline.value ?? []) as Bar[];
  if (tf.value === "1m") return bars;
  if (tf.value === "5m") return aggregateBars(bars, 5);
  if (tf.value === "15m") return aggregateBars(bars, 15);
  return aggregateBars(bars, 60);
});

function upsertBarFromTrade(t: { price: unknown; qty: unknown; createdAt: string }) {
  const pd = pairMeta.value?.priceDecimals ?? 8;
  const qd = pairMeta.value?.qtyDecimals ?? 8;
  const price = atomicToNumber(t.price);
  const qty = atomicToNumber(t.qty);
  const ts = Date.parse(t.createdAt);
  if (!Number.isFinite(price) || !Number.isFinite(qty) || !Number.isFinite(ts)) return;
  const sec = Math.floor(ts / 1000);
  const bucket = sec - (sec % 60);
  const bucketIso = new Date(bucket * 1000).toISOString();
  const last = kline.value[kline.value.length - 1] as any;
  if (last && last.t === bucketIso) {
    last.h = Math.max(atomicToNumber(last.h), price);
    last.l = Math.min(atomicToNumber(last.l), price);
    last.c = price;
    last.v = atomicToNumber(last.v) + qty;
    kline.value = [...kline.value.slice(0, -1), last];
  } else {
    const bar = { t: bucketIso, o: price, h: price, l: price, c: price, v: qty };
    kline.value = [...kline.value, bar].slice(-300);
  }
}

function updateBestPrices() {
  const pd = pairMeta.value?.priceDecimals ?? 8;
  bestBid.value = book.bids?.[0]?.price != null ? atomicToNumber(book.bids[0].price) : undefined;
  bestAsk.value = book.asks?.[0]?.price != null ? atomicToNumber(book.asks[0].price) : undefined;
}

onMounted(() => {
  pageDisposed = false;
  chartAlive.value = true;
  reloadAll();
});

onBeforeRouteLeave(() => {
  pageDisposed = true;
  // 提前卸载第三方图表，避免路由切换与图表 DOM 清理并发触发 parentNode 异常。
  chartAlive.value = false;
  disconnectWs();
});

onUnmounted(() => {
  pageDisposed = true;
  chartAlive.value = false;
  disconnectWs();
});
</script>

<style scoped>
.trade-terminal {
  display: grid;
  gap: 10px;
}

.trade-topbar {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: rgba(18, 22, 31, 0.96);
}

.trade-topbar-left,
.trade-topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.trade-topbar-left {
  min-width: 0;
  flex: 1;
}

.trade-pair-box {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.trade-pair-main {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.trade-pair-select {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text);
  font-size: 18px;
  font-weight: 700;
  padding: 0;
  min-width: 130px;
  outline: none;
}

.trade-last {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trade-last-price {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.trade-last-change {
  font-size: 12px;
}

.trade-stats-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.trade-stat {
  display: grid;
  gap: 2px;
}

.trade-stat-label {
  color: var(--muted);
  font-size: 11px;
}

.trade-stat-value {
  font-size: 13px;
}

.trade-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px 320px;
  gap: 10px;
  min-height: 760px;
}

.trade-chart-shell,
.trade-market-shell,
.trade-order-shell {
  min-width: 0;
}

.trade-chart-shell {
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.trade-chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.trade-chart-tabs,
.trade-chart-actions,
.trade-indicator-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.trade-tab-btn,
.trade-chip-btn {
  border: none;
  background: transparent;
  color: var(--muted);
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
}

.trade-tab-btn.active,
.trade-chip-btn.active {
  color: var(--text);
  background: rgba(255, 255, 255, 0.08);
}

.trade-indicator-label {
  color: var(--muted);
  font-size: 12px;
}

.trade-chart-body {
  padding: 0 8px 8px;
}

.trade-market-shell,
.trade-order-shell {
  display: grid;
  gap: 10px;
  align-content: start;
}

.trade-market-shell {
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.trade-market-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.trade-market-extra {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
}

.trade-market-body {
  min-height: 0;
}

.trade-market-body :deep(.panel) {
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
  border: none;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}

.trade-market-body :deep(.panel-header) {
  display: none;
}

.trade-market-body :deep(.panel-body) {
  min-height: 0;
}

.trade-balance-card {
  overflow: hidden;
}

.trade-balance-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);
}

.trade-balance-row:last-child {
  border-bottom: none;
}

.trade-bottom {
  min-width: 0;
}

.trade-error {
  margin-top: 2px;
}

@media (max-width: 1440px) {
  .trade-main-grid {
    grid-template-columns: minmax(0, 1fr) 260px 300px;
  }
}

@media (max-width: 1200px) {
  .trade-main-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .trade-topbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
