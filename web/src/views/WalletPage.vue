<template>
  <div style="display: grid; gap: 16px">
    <h3>资产</h3>
    <div v-if="!auth.token" style="color: #c00">请先登录</div>
    <div v-else style="display: grid; gap: 16px">
      <section style="border: 1px solid #ddd; padding: 12px">
        <div style="display: flex; gap: 10px; justify-content: space-between; align-items: center">
          <strong>余额</strong>
          <button @click="refresh" :disabled="loading">刷新</button>
        </div>
        <table style="width: 100%; border-collapse: collapse; margin-top: 8px">
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
      </section>

      <section style="border: 1px solid #ddd; padding: 12px; display: grid; gap: 10px">
        <strong>充值（开发环境模拟）</strong>
        <div style="display: flex; gap: 8px; flex-wrap: wrap">
          <label>
            资产
            <select v-model="dep.asset">
              <option value="USDT">USDT</option>
              <option value="BTC">BTC</option>
              <option value="ETH">ETH</option>
            </select>
          </label>
          <label>
            金额
            <input v-model="dep.amount" />
          </label>
          <button @click="getAddress" :disabled="loading">获取充值地址</button>
        </div>
        <div v-if="dep.address">地址：{{ dep.address }}</div>
        <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
          <label>
            AdminKey
            <input v-model="dep.adminKey" style="width: 180px" />
          </label>
          <button @click="simulateDeposit" :disabled="loading">模拟到账</button>
          <span v-if="dep.msg" style="color: #060">{{ dep.msg }}</span>
        </div>
      </section>

      <section style="border: 1px solid #ddd; padding: 12px; display: grid; gap: 10px">
        <strong>提现</strong>
        <div style="display: flex; gap: 8px; flex-wrap: wrap">
          <label>
            资产
            <select v-model="wd.asset">
              <option value="USDT">USDT</option>
              <option value="BTC">BTC</option>
              <option value="ETH">ETH</option>
            </select>
          </label>
          <label>
            地址
            <input v-model="wd.address" style="width: 260px" />
          </label>
          <label>
            金额
            <input v-model="wd.amount" />
          </label>
          <label>
            资金密码
            <input v-model="wd.fundPassword" type="password" style="width: 120px" />
          </label>
          <button @click="withdraw" :disabled="loading">发起提现</button>
        </div>
        <div v-if="wd.msg" style="color: #060">{{ wd.msg }}</div>
      </section>

      <section style="border: 1px solid #ddd; padding: 12px">
        <strong>充值记录</strong>
        <table style="width: 100%; border-collapse: collapse; margin-top: 8px">
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
      </section>

      <section style="border: 1px solid #ddd; padding: 12px">
        <strong>提现记录</strong>
        <table style="width: 100%; border-collapse: collapse; margin-top: 8px">
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
                <button v-if="w.status === 'PENDING'" @click="cancelWithdraw(w.id)" :disabled="loading">撤销</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <div v-if="err" style="color: #c00">{{ err }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
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
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
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
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
  } finally {
    loading.value = false;
  }
}

async function cancelWithdraw(id: string) {
  err.value = "";
  loading.value = true;
  try {
    await http.post(`/api/account/withdraw/${id}/cancel`);
    await refresh();
  } catch (e: any) {
    err.value = apiErr(e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  refresh();
});
</script>
