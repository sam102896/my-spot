<template>
  <div class="panel" style="max-width: 560px">
    <div class="panel-header">
      <div class="panel-title">基础 KYC</div>
      <span class="badge">
        <span :class="['dot', auth.me?.kycStatus === 'VERIFIED' ? 'ok' : 'warn']" />
        <span class="mono">{{ auth.me?.kycStatus ?? "-" }}</span>
      </span>
    </div>
    <div class="panel-body">
      <div v-if="!auth.token" class="badge" style="border-color: rgba(239, 68, 68, 0.25)">
        <span class="dot bad" />
        <span>请先登录</span>
      </div>
      <div v-else style="display: grid; gap: 10px">
        <label style="display: grid; gap: 6px">
          <div class="muted">姓名</div>
          <input v-model="name" class="input" />
        </label>
        <button class="btn btn-primary" @click="submit" :disabled="loading" style="padding: 12px 12px; font-weight: 800">
          {{ loading ? "提交中…" : "提交认证" }}
        </button>
        <div v-if="msg" class="badge" style="border-color: rgba(22, 163, 74, 0.28)">
          <span class="dot ok" />
          <span class="mono">{{ msg }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useToastStore } from "../stores/toast";

const auth = useAuthStore();
const toast = useToastStore();
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
    await http.post("/api/account/kyc", { name: name.value });
    msg.value = "认证成功";
    const meRes = await http.get("/api/account/me");
    auth.me = meRes.data;
    toast.push("KYC", "认证成功");
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("KYC", err.value);
  } finally {
    loading.value = false;
  }
}
</script>
