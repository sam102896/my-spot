<template>
  <div style="display: grid; gap: 12px; max-width: 520px">
    <h3>基础KYC</h3>
    <div v-if="!auth.token" style="color: #c00">请先登录</div>
    <div v-else style="display: grid; gap: 10px">
      <div>当前状态：{{ auth.me?.kycStatus ?? "-" }}</div>
      <label>
        姓名
        <input v-model="name" style="width: 100%" />
      </label>
      <button @click="submit" :disabled="loading">提交认证</button>
      <div v-if="msg" style="color: #060">{{ msg }}</div>
      <div v-if="err" style="color: #c00">{{ err }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const name = ref(auth.me?.name ?? "");
const loading = ref(false);
const err = ref("");
const msg = ref("");

function apiErr(e: any): string {
  return e?.response?.data?.message ?? e?.message ?? "请求失败";
}

async function submit() {
  err.value = "";
  msg.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/account/kyc", { name: name.value });
    msg.value = "认证成功";
    const meRes = await http.get("/api/account/me");
    auth.me = meRes.data;
  } catch (e: any) {
    err.value = apiErr(e);
  } finally {
    loading.value = false;
  }
}
</script>

