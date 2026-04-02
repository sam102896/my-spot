<template>
  <div style="max-width: 1100px; margin: 0 auto; padding: 16px">
    <header style="display: flex; gap: 12px; align-items: center; justify-content: space-between">
      <div style="display: flex; gap: 10px; align-items: center">
        <strong>My Spot</strong>
        <a href="/trade">交易</a>
        <a href="/wallet">资产</a>
        <a href="/kyc">KYC</a>
        <a href="/login">登录</a>
      </div>
      <div style="display: flex; gap: 10px; align-items: center">
        <span v-if="auth.me"> {{ auth.me.email || auth.me.phone }} / {{ auth.me.kycStatus }} </span>
        <button v-if="auth.token" @click="logout">退出</button>
      </div>
    </header>
    <hr />
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { RouterView, useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";
import { http } from "./api/http";

const auth = useAuthStore();
const router = useRouter();

async function loadMe() {
  if (!auth.token) return;
  const res = await http.get("/api/account/me");
  auth.me = res.data;
}

async function logout() {
  auth.clear();
  await router.push("/login");
}

onMounted(() => {
  loadMe().catch(() => {
    auth.clear();
  });
});
</script>

