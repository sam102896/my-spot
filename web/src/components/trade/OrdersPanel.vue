<template>
  <div class="panel">
    <div class="panel-header">
      <div class="panel-title">订单</div>
      <div style="display: flex; gap: 8px; align-items: center">
        <button class="btn" :class="tab === 'open' ? 'btn-primary' : ''" @click="tab = 'open'">当前委托</button>
        <button class="btn" :class="tab === 'hist' ? 'btn-primary' : ''" @click="tab = 'hist'">历史订单</button>
      </div>
    </div>

    <div class="panel-body" style="padding: 0">
      <div class="panel-header" style="border-bottom: 1px solid rgba(148, 163, 184, 0.12)">
        <div class="muted" style="font-size: 12px">筛选</div>
        <div style="display: flex; gap: 10px; align-items: center">
          <select class="select" style="width: 160px" v-model="statusFilter">
            <option value="">全部状态</option>
            <option value="NEW">NEW</option>
            <option value="PARTIALLY_FILLED">PARTIALLY_FILLED</option>
            <option value="FILLED">FILLED</option>
            <option value="CANCELED">CANCELED</option>
            <option value="REJECTED">REJECTED</option>
          </select>
          <select class="select" style="width: 160px" v-model="sortBy">
            <option value="time">时间</option>
            <option value="price">价格</option>
            <option value="qty">数量</option>
          </select>
        </div>
      </div>

      <div class="scroll" style="max-height: 540px">
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>方向</th>
              <th>类型</th>
              <th style="text-align: right">价格</th>
              <th style="text-align: right">数量</th>
              <th style="text-align: right">已成交</th>
              <th>状态</th>
              <th style="text-align: right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in shown" :key="o.id">
              <td class="mono">{{ shortId(o.id) }}</td>
              <td class="mono" :style="{ color: o.side === 'BUY' ? 'var(--buy)' : 'var(--sell)' }">{{ o.side }}</td>
              <td class="mono">{{ o.type }}</td>
              <td class="mono" style="text-align: right">{{ o.price == null ? "-" : fmtPrice(o.price) }}</td>
              <td class="mono" style="text-align: right">{{ fmtQty(o.origQty) }}</td>
              <td class="mono" style="text-align: right">{{ fmtQty(o.filledQty) }}</td>
              <td>
                <span class="badge" :style="statusStyle(o.status)">{{ o.status }}</span>
              </td>
              <td style="text-align: right">
                <button
                  v-if="tab === 'open'"
                  class="btn btn-sell"
                  style="padding: 8px 10px"
                  @click="askCancel(o.id)"
                  :disabled="loading"
                >
                  撤单
                </button>
              </td>
            </tr>
            <tr v-if="shown.length === 0">
              <td colspan="8" class="muted" style="padding: 16px; text-align: center">
                {{ loading ? "加载中…" : "暂无数据" }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <ConfirmDialog
      :open="confirmOpen"
      title="确认撤单"
      :message="`确认撤销订单 ${confirmId ? shortId(confirmId) : ''} ?`"
      confirmText="确认撤单"
      cancelText="取消"
      @cancel="confirmOpen = false"
      @confirm="doCancel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { atomicToNumber, formatNumber } from "../../utils/format";
import ConfirmDialog from "../ConfirmDialog.vue";

type OrderLike = {
  id: string;
  side: string;
  type: string;
  price?: unknown | null;
  origQty: unknown;
  filledQty: unknown;
  status: string;
  createdAt?: string;
};

const props = defineProps<{
  openOrders: OrderLike[];
  historyOrders: OrderLike[];
  loading: boolean;
  priceDecimals?: number;
  qtyDecimals?: number;
}>();

const emit = defineEmits<{
  (e: "cancel", id: string): void;
}>();

const tab = ref<"open" | "hist">("open");
const statusFilter = ref<string>("");
const sortBy = ref<"time" | "price" | "qty">("time");

const list = computed(() => (tab.value === "open" ? props.openOrders : props.historyOrders));

function fmtPrice(v: unknown): string {
  const d = props.priceDecimals ?? 8;
  return formatNumber(atomicToNumber(v), { decimals: d });
}

function fmtQty(v: unknown): string {
  const d = props.qtyDecimals ?? 8;
  return formatNumber(atomicToNumber(v), { decimals: d });
}

function shortId(id: string): string {
  if (!id) return "-";
  return id.length > 10 ? `${id.slice(0, 6)}…${id.slice(-4)}` : id;
}

function statusStyle(s: string): Record<string, string> {
  if (s === "NEW") return { borderColor: "rgba(96,165,250,0.3)", background: "rgba(96,165,250,0.14)" };
  if (s === "PARTIALLY_FILLED") return { borderColor: "rgba(245,158,11,0.32)", background: "rgba(245,158,11,0.14)" };
  if (s === "FILLED") return { borderColor: "rgba(22,163,74,0.32)", background: "rgba(22,163,74,0.14)" };
  if (s === "CANCELED") return { borderColor: "rgba(148,163,184,0.26)", background: "rgba(148,163,184,0.12)" };
  if (s === "REJECTED") return { borderColor: "rgba(239,68,68,0.28)", background: "rgba(239,68,68,0.12)" };
  return { borderColor: "rgba(148,163,184,0.22)", background: "rgba(148,163,184,0.1)" };
}

const shown = computed(() => {
  const filtered = (list.value ?? []).filter((o) => {
    if (!statusFilter.value) return true;
    return o.status === statusFilter.value;
  });

  const sorted = [...filtered];
  const dir = -1;

  sorted.sort((a, b) => {
    if (sortBy.value === "time") {
      const taRaw = a.createdAt ? Date.parse(a.createdAt) : NaN;
      const tbRaw = b.createdAt ? Date.parse(b.createdAt) : NaN;
      const ta = Number.isFinite(taRaw) ? taRaw : 0;
      const tb = Number.isFinite(tbRaw) ? tbRaw : 0;
      return (ta - tb) * dir;
    }
    if (sortBy.value === "price") {
      const paRaw = a.price == null ? NaN : atomicToNumber(a.price);
      const pbRaw = b.price == null ? NaN : atomicToNumber(b.price);
      const pa = Number.isFinite(paRaw) ? paRaw : 0;
      const pb = Number.isFinite(pbRaw) ? pbRaw : 0;
      return (pa - pb) * dir;
    }
    const qaRaw = atomicToNumber(a.origQty);
    const qbRaw = atomicToNumber(b.origQty);
    const qa = Number.isFinite(qaRaw) ? qaRaw : 0;
    const qb = Number.isFinite(qbRaw) ? qbRaw : 0;
    return (qa - qb) * dir;
  });
  return sorted;
});

const confirmOpen = ref(false);
const confirmId = ref<string>("");

function askCancel(id: string) {
  confirmId.value = id;
  confirmOpen.value = true;
}

function doCancel() {
  const id = confirmId.value;
  confirmOpen.value = false;
  confirmId.value = "";
  if (id) emit("cancel", id);
}
</script>
