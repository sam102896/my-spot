<template>
  <div style="display: grid; gap: 14px">
    <div class="panel">
      <div class="panel-header">
        <div class="panel-title">资产</div>
        <button class="btn btn-primary" @click="refresh" :disabled="loading">{{ loading ? "刷新中…" : "刷新" }}</button>
      </div>
      <div class="panel-body">
        <div v-if="!auth.token" class="badge" style="border-color: rgba(239, 68, 68, 0.25)">
          <span class="dot bad" />
          <span>请先登录</span>
        </div>
        <div v-else class="muted" style="font-size: 12px">余额 / 充值 / 提现 / 记录</div>
      </div>
    </div>

    <div v-if="auth.token" style="display: grid; gap: 14px">
      <section class="panel">
        <div class="panel-header">
          <div class="panel-title">余额</div>
        </div>
        <div class="panel-body" style="padding: 0">
          <table class="table">
          <thead>
            <tr>
              <th align="left">币种</th>
              <th align="right">可用</th>
              <th align="right">冻结</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="w in wallets" :key="w.asset">
              <td>{{ w.asset }}</td>
              <td align="right">{{ fmtAmount(w.available) }}</td>
              <td align="right">{{ fmtAmount(w.frozen) }}</td>
            </tr>
          </tbody>
        </table>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div class="panel-title">充值（开发环境模拟）</div>
        </div>
        <div class="panel-body" style="display: grid; gap: 10px">
          <div style="display: flex; gap: 10px; flex-wrap: wrap">
            <label style="display: grid; gap: 6px; min-width: 120px">
              <div class="muted">资产</div>
              <select v-model="dep.asset" class="select">
              <option value="USDT">USDT</option>
              <option value="BTC">BTC</option>
              <option value="ETH">ETH</option>
            </select>
            </label>
            <label style="display: grid; gap: 6px; min-width: 140px">
              <div class="muted">金额</div>
              <input v-model="dep.amount" class="input mono" />
            </label>
            <button class="btn btn-primary btn-sm" @click="getAddress" :disabled="loading">获取充值地址</button>
          </div>
          <div v-if="dep.address" class="badge" style="width: fit-content">
            <span class="muted">地址</span>
            <span class="mono">{{ dep.address }}</span>
          </div>
          <div style="display: flex; gap: 10px; flex-wrap: wrap; align-items: end">
            <label style="display: grid; gap: 6px; min-width: 240px">
              <div class="muted">AdminKey</div>
              <input v-model="dep.adminKey" class="input mono" />
            </label>
            <button class="btn btn-buy btn-sm" @click="simulateDeposit" :disabled="loading">模拟到账</button>
            <span v-if="dep.msg" class="badge" style="border-color: rgba(22, 163, 74, 0.28)">
              <span class="dot ok" />
              <span class="mono">{{ dep.msg }}</span>
            </span>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div class="panel-title">提现</div>
        </div>
        <div class="panel-body" style="display: grid; gap: 10px">
          <div style="display: flex; gap: 10px; flex-wrap: wrap">
            <label style="display: grid; gap: 6px; min-width: 120px">
              <div class="muted">资产</div>
              <select v-model="wd.asset" class="select">
              <option value="USDT">USDT</option>
              <option value="BTC">BTC</option>
              <option value="ETH">ETH</option>
            </select>
            </label>
            <label style="display: grid; gap: 6px; min-width: 320px; flex: 1">
              <div class="muted">地址</div>
              <input v-model="wd.address" class="input mono" />
            </label>
            <label style="display: grid; gap: 6px; min-width: 140px">
              <div class="muted">金额</div>
              <input v-model="wd.amount" class="input mono" />
            </label>
            <label style="display: grid; gap: 6px; min-width: 140px">
              <div class="muted">资金密码</div>
              <input v-model="wd.fundPassword" class="input mono" type="password" />
            </label>
            <button class="btn btn-sell btn-sm" @click="withdraw" :disabled="loading" style="font-weight: 800">发起提现</button>
          </div>
          <div v-if="wd.msg" class="badge" style="border-color: rgba(22, 163, 74, 0.28)">
            <span class="dot ok" />
            <span class="mono">{{ wd.msg }}</span>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div class="panel-title">充值记录</div>
        </div>
        <div class="panel-body" style="padding: 0">
          <table class="table">
          <thead>
            <tr>
              <th align="left">ID</th>
              <th align="left">币种</th>
              <th align="right">金额</th>
              <th align="left">状态</th>
              <th align="left">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in deposits" :key="d.id">
              <td>{{ d.id }}</td>
              <td>{{ d.asset }}</td>
              <td align="right">{{ fmtAmount(d.amount) }}</td>
              <td>{{ d.status }}</td>
              <td>{{ d.createdAt }}</td>
            </tr>
          </tbody>
        </table>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div class="panel-title">提现记录</div>
        </div>
        <div class="panel-body" style="padding: 0">
          <table class="table">
          <thead>
            <tr>
              <th align="left">ID</th>
              <th align="left">币种</th>
              <th align="right">金额</th>
              <th align="right">手续费</th>
              <th align="left">状态</th>
              <th align="left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="w in withdrawals" :key="w.id">
              <td>{{ w.id }}</td>
              <td>{{ w.asset }}</td>
              <td align="right">{{ fmtAmount(w.amount) }}</td>
              <td align="right">{{ fmtAmount(w.fee) }}</td>
              <td>{{ w.status }}</td>
              <td>
                <button v-if="w.status === 'PENDING'" class="btn btn-sell" @click="askCancel(w.id)" :disabled="loading">
                  撤销
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </section>

      <div v-if="err" class="badge" style="border-color: rgba(239, 68, 68, 0.25)">
        <span class="dot bad" />
        <span>{{ err }}</span>
      </div>

      <ConfirmDialog
        :open="confirmOpen"
        title="确认撤销提现吗？"
        :message="`确认撤销提现 ${confirmId ? confirmId.slice(0, 6) + '…' + confirmId.slice(-4) : ''} ?`"
        confirmText="确认撤销"
        cancelText="取消"
        @cancel="confirmOpen = false"
        @confirm="confirmCancel"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useToastStore } from "../stores/toast";
import { formatAtomic } from "../utils/format";

const auth = useAuthStore();
const toast = useToastStore();
const loading = ref(false);
const err = ref("");

const wallets = ref<any[]>([]);
const deposits = ref<any[]>([]);
const withdrawals = ref<any[]>([]);

const dep = reactive({
  asset: "USDT",
  amount: "100",
  address: "",
  adminKey: "dev-admin-key",
  msg: ""
});

const wd = reactive({
  asset: "USDT",
  address: "ADDR-USDT-EXTERNAL",
  amount: "10",
  fundPassword: "123456",
  msg: ""
});

function fmtAmount(v: unknown): string {
  return formatAtomic(v, 8);
}

function apiErr(e: any): string {
  return e?.response?.data?.message ?? e?.message ?? "请求失败";
}

async function refresh() {
  if (!auth.token) return;
  err.value = "";
  loading.value = true;
  try {
    const [wRes, dRes, wdRes] = await Promise.all([
      http.get("/api/account/wallets"),
      http.get("/api/account/deposits?limit=20"),
      http.get("/api/account/withdrawals?limit=20")
    ]);
    wallets.value = wRes.data;
    deposits.value = dRes.data;
    withdrawals.value = wdRes.data;
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Wallet", err.value);
  } finally {
    loading.value = false;
  }
}

async function getAddress() {
  dep.msg = "";
  err.value = "";
  loading.value = true;
  try {
    const res = await http.get(`/api/account/deposit/address?asset=${dep.asset}`);
    dep.address = res.data.address;
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Deposit", err.value);
  } finally {
    loading.value = false;
  }
}

async function simulateDeposit() {
  dep.msg = "";
  err.value = "";
  loading.value = true;
  try {
    const identifier = auth.me?.email || auth.me?.phone;
    if (!identifier) throw new Error("缺少账号信息");
    const res = await http.post(
      "/api/public/admin/deposits/simulate",
      { identifier, asset: dep.asset, amount: dep.amount },
      { headers: { "X-Admin-Key": dep.adminKey } }
    );
    dep.msg = `已创建充值：${res.data.id}，约5秒后自动确认`;
    toast.push("Deposit", "充值已创建，等待确认");
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Deposit", err.value);
  } finally {
    loading.value = false;
  }
}

async function withdraw() {
  wd.msg = "";
  err.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/account/withdraw", {
      asset: wd.asset,
      address: wd.address,
      amount: wd.amount,
      fundPassword: wd.fundPassword
    });
    wd.msg = `提现已提交：${res.data.id}（状态${res.data.status}）`;
    toast.push("Withdraw", "提现已提交");
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Withdraw", err.value);
  } finally {
    loading.value = false;
  }
}

async function cancelWithdraw(id: string) {
  err.value = "";
  loading.value = true;
  try {
    await http.post(`/api/account/withdraw/${id}/cancel`);
    toast.push("Withdraw", "已撤销");
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Withdraw", err.value);
  } finally {
    loading.value = false;
  }
}

const confirmOpen = ref(false);
const confirmId = ref("");

function askCancel(id: string) {
  confirmId.value = id;
  confirmOpen.value = true;
}

function confirmCancel() {
  const id = confirmId.value;
  confirmOpen.value = false;
  confirmId.value = "";
  if (id) cancelWithdraw(id);
}

onMounted(() => {
  refresh();
});
</script>
