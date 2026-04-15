<template>
  <div>
    <div style="display: flex; gap: 10px; align-items: center; justify-content: space-between; flex-wrap: wrap">
      <div class="badge">
        <span class="muted">交易对</span>
        <select class="select" style="width: 160px" v-model="pair" @change="reloadAll">
          <option v-for="p in pairs" :key="p.symbol" :value="p.symbol">{{ p.symbol }}</option>
        </select>
      </div>
      <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
        <span v-if="!auth.token" class="badge" style="border-color: rgba(239, 68, 68, 0.25)">
          <span class="dot bad" />
          <span>请先登录并完成 KYC 后交易</span>
        </span>
        <span class="badge">
          <span :class="['dot', wsDot]" />
          <span class="mono muted">WS: {{ wsStatus || "offline" }}</span>
        </span>
        <button class="btn btn-primary" @click="reloadAll" :disabled="loading">刷新</button>
      </div>
    </div>

    <div class="grid-3">
      <div class="col-left" style="display: grid; gap: 14px">
        <OrderBookPanel
          title="盘口"
          :bids="book.bids"
          :asks="book.asks"
          :ws-status="wsStatus"
          :loading="loading"
          :price-decimals="pairMeta?.priceDecimals ?? 8"
          :qty-decimals="pairMeta?.qtyDecimals ?? 8"
          @refresh="loadMarket"
          @select-price="onSelectPrice"
        />
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
      </div>

      <div class="col-mid" style="display: grid; gap: 14px">
        <div class="panel">
          <div class="panel-header">
            <div class="panel-title">
              <span>图表</span>
              <span class="muted mono">{{ pair }}</span>
            </div>
            <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
              <span class="badge" style="gap: 10px">
                <span class="muted">最新</span>
                <span class="mono">{{ lastPriceText }}</span>
                <span class="muted">·</span>
                <span class="mono" :style="{ color: changeColor }">{{ changeText }}</span>
              </span>
              <span class="badge" style="gap: 10px">
                <span class="muted">{{ statsPeriodText }}</span>
                <span class="muted">高</span>
                <span class="mono">{{ statsHighText }}</span>
                <span class="muted">低</span>
                <span class="mono">{{ statsLowText }}</span>
                <span class="muted">量</span>
                <span class="mono">{{ statsVolText }}</span>
              </span>
              <div style="display: flex; gap: 6px">
                <button class="btn" :class="tf === '1m' ? 'btn-primary' : ''" @click="tf = '1m'">1m</button>
                <button class="btn" :class="tf === '5m' ? 'btn-primary' : ''" @click="tf = '5m'">5m</button>
                <button class="btn" :class="tf === '15m' ? 'btn-primary' : ''" @click="tf = '15m'">15m</button>
                <button class="btn" :class="tf === '1h' ? 'btn-primary' : ''" @click="tf = '1h'">1h</button>
              </div>
              <div class="badge" style="gap: 6px">
                <span class="muted">MA</span>
                <button class="btn" :class="showMa5 ? 'btn-primary' : ''" @click="showMa5 = !showMa5">5</button>
                <button class="btn" :class="showMa10 ? 'btn-primary' : ''" @click="showMa10 = !showMa10">10</button>
                <button class="btn" :class="showMa20 ? 'btn-primary' : ''" @click="showMa20 = !showMa20">20</button>
              </div>
            </div>
          </div>
          <div class="panel-body">
            <KlineTv v-if="chartBars.length > 0" :bars="chartBars" :ma="maList" />
          </div>
        </div>
      </div>

      <div class="col-right" style="display: grid; gap: 14px">
        <TradesPanel
          :pair="pair"
          :trades="trades"
          :price-decimals="pairMeta?.priceDecimals ?? 8"
          :qty-decimals="pairMeta?.qtyDecimals ?? 8"
        />
        <OrdersPanel
          :open-orders="openOrders"
          :history-orders="historyOrders"
          :loading="loading"
          :price-decimals="pairMeta?.priceDecimals ?? 8"
          :qty-decimals="pairMeta?.qtyDecimals ?? 8"
          @cancel="cancel"
        />
      </div>
    </div>

    <div v-if="err" class="badge" style="margin-top: 12px; border-color: rgba(239, 68, 68, 0.25)">
      <span class="dot bad" />
      <span>{{ err }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useToastStore } from "../stores/toast";
import { atomicToNumber, formatNumber, toNumber } from "../utils/format";
import KlineTv from "../components/trade/KlineTv.vue";
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

function apiErr(e: any): string {
  return e?.response?.data?.message ?? e?.message ?? "请求失败";
}

async function loadPairs() {
  const res = await http.get("/api/public/market/pairs");
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
  openOrders.value = Array.isArray(oRes.data) ? oRes.data : [];
  historyOrders.value = Array.isArray(hRes.data) ? hRes.data : [];
}

async function loadWallets() {
  if (!auth.token) return;
  const res = await http.get("/api/account/wallets");
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
  err.value = "";
  loading.value = true;
  try {
    await loadPairs();
    await loadMarket();
    await loadOrders();
    await loadWallets();
    connectWs();
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Trade", err.value);
  } finally {
    loading.value = false;
  }
}

async function placeOrder() {
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
    err.value = apiErr(e);
    toast.push("Order", err.value);
  } finally {
    loading.value = false;
  }
}

async function cancel(id: string) {
  err.value = "";
  loading.value = true;
  try {
    await http.post(`/api/trade/order/${id}/cancel`);
    await loadOrders();
    await loadMarket();
    await loadWallets();
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Cancel", err.value);
  } finally {
    loading.value = false;
  }
}

function connectWs() {
  if (ws) {
    ws.close();
    ws = null;
  }
  wsStatus.value = "connecting";
  ws = new WebSocket(`ws://localhost:3001/api/public/ws/market?pair=${pair.value}`);
  ws.onopen = () => (wsStatus.value = "open");
  ws.onclose = () => (wsStatus.value = "closed");
  ws.onerror = () => (wsStatus.value = "error");
  ws.onmessage = (ev) => {
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
  reloadAll();
});

onUnmounted(() => {
  if (ws) ws.close();
});
</script>
