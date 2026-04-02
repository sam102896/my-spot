<template>
  <div style="display: grid; gap: 12px">
    <h3>现货交易</h3>
    <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
      <label>
        交易对
        <select v-model="pair" @change="reloadAll">
          <option v-for="p in pairs" :key="p.symbol" :value="p.symbol">{{ p.symbol }}</option>
        </select>
      </label>
      <button @click="reloadAll" :disabled="loading">刷新</button>
      <span v-if="wsStatus" style="color: #666">WS: {{ wsStatus }}</span>
    </div>

    <div v-if="!auth.token" style="color: #c00">交易需先登录，并完成基础KYC</div>

    <div style="display: grid; gap: 12px; grid-template-columns: 1fr 1fr">
      <section style="border: 1px solid #ddd; padding: 12px">
        <strong>盘口（Top5）</strong>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 8px">
          <div>
            <div style="color: #060">买盘</div>
            <table style="width: 100%; border-collapse: collapse; font-size: 12px">
              <thead>
                <tr>
                  <th align="right">价</th>
                  <th align="right">量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in book.bids" :key="b.price">
                  <td align="right">{{ fmtPrice(b.price) }}</td>
                  <td align="right">{{ fmtQty(b.qty) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div>
            <div style="color: #c00">卖盘</div>
            <table style="width: 100%; border-collapse: collapse; font-size: 12px">
              <thead>
                <tr>
                  <th align="right">价</th>
                  <th align="right">量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in book.asks" :key="a.price">
                  <td align="right">{{ fmtPrice(a.price) }}</td>
                  <td align="right">{{ fmtQty(a.qty) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section style="border: 1px solid #ddd; padding: 12px">
        <strong>成交（最近）</strong>
        <table style="width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px">
          <thead>
            <tr>
              <th align="right">价</th>
              <th align="right">量</th>
              <th align="left">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in trades" :key="t.createdAt + ':' + t.price + ':' + t.qty">
              <td align="right">{{ fmtPrice(t.price) }}</td>
              <td align="right">{{ fmtQty(t.qty) }}</td>
              <td>{{ t.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>

    <section style="border: 1px solid #ddd; padding: 12px; display: grid; gap: 10px">
      <strong>下单</strong>
      <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
        <label>
          方向
          <select v-model="order.side">
            <option value="BUY">买入</option>
            <option value="SELL">卖出</option>
          </select>
        </label>
        <label>
          类型
          <select v-model="order.type">
            <option value="LIMIT">限价</option>
            <option value="MARKET">市价</option>
          </select>
        </label>
        <label v-if="order.type === 'LIMIT'">
          价格
          <input v-model="order.price" />
        </label>
        <label>
          数量
          <input v-model="order.qty" />
        </label>
        <button @click="placeOrder" :disabled="loading || !auth.token">提交</button>
        <span v-if="order.msg" style="color: #060">{{ order.msg }}</span>
      </div>
    </section>

    <section style="border: 1px solid #ddd; padding: 12px">
      <strong>当前委托</strong>
      <table style="width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px">
        <thead>
          <tr>
            <th align="left">ID</th>
            <th align="left">方向</th>
            <th align="left">类型</th>
            <th align="right">价格</th>
            <th align="right">数量</th>
            <th align="right">已成交</th>
            <th align="left">状态</th>
            <th align="left">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in openOrders" :key="o.id">
            <td>{{ o.id }}</td>
            <td>{{ o.side }}</td>
            <td>{{ o.type }}</td>
            <td align="right">{{ o.price == null ? "-" : fmtPrice(o.price) }}</td>
            <td align="right">{{ fmtQty(o.origQty) }}</td>
            <td align="right">{{ fmtQty(o.filledQty) }}</td>
            <td>{{ o.status }}</td>
            <td>
              <button @click="cancel(o.id)" :disabled="loading">撤单</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section style="border: 1px solid #ddd; padding: 12px">
      <strong>K线（1m，简化）</strong>
      <table style="width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px">
        <thead>
          <tr>
            <th align="left">时间(UTC)</th>
            <th align="right">开</th>
            <th align="right">高</th>
            <th align="right">低</th>
            <th align="right">收</th>
            <th align="right">量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in kline" :key="b.t">
            <td>{{ b.t }}</td>
            <td align="right">{{ fmtPrice(b.o) }}</td>
            <td align="right">{{ fmtPrice(b.h) }}</td>
            <td align="right">{{ fmtPrice(b.l) }}</td>
            <td align="right">{{ fmtPrice(b.c) }}</td>
            <td align="right">{{ fmtQty(b.v) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="err" style="color: #c00">{{ err }}</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from "vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const loading = ref(false);
const err = ref("");

const pairs = ref<any[]>([]);
const pair = ref("BTCUSDT");
const book = reactive({ bids: [] as any[], asks: [] as any[] });
const trades = ref<any[]>([]);
const kline = ref<any[]>([]);
const openOrders = ref<any[]>([]);

const order = reactive({
  side: "BUY",
  type: "LIMIT",
  price: "65000",
  qty: "0.001",
  msg: ""
});

const wsStatus = ref("");
let ws: WebSocket | null = null;

function formatAtomic(v: unknown, decimals = 8): string {
  if (v === null || v === undefined) return "-";
  let bi: bigint;
  try {
    if (typeof v === "bigint") bi = v;
    else bi = BigInt(String(v));
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

function fmtPrice(v: unknown): string {
  return formatAtomic(v, 8);
}

function fmtQty(v: unknown): string {
  return formatAtomic(v, 8);
}

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
}

async function loadOrders() {
  if (!auth.token) {
    openOrders.value = [];
    return;
  }
  const res = await http.get("/api/trade/open-orders?limit=50");
  openOrders.value = res.data.filter((o: any) => o.pairId);
}

async function reloadAll() {
  err.value = "";
  loading.value = true;
  try {
    await loadPairs();
    await loadMarket();
    await loadOrders();
    connectWs();
  } catch (e: any) {
    err.value = apiErr(e);
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
  } catch (e: any) {
    err.value = apiErr(e);
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
  } catch (e: any) {
    err.value = apiErr(e);
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
  ws.onmessage = () => {
    loadMarket().catch(() => {});
  };
}

onMounted(() => {
  reloadAll();
});

onUnmounted(() => {
  if (ws) ws.close();
});
</script>
