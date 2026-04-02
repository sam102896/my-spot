<template>
  <div class="container">
    <header class="topbar">
      <div class="topbar-left">
        <div class="brand">
          <span class="logo" />
          <span>my-spot</span>
        </div>
        <nav class="nav">
          <RouterLink class="nav-link" to="/trade">{{ t.navTrade }}</RouterLink>
          <RouterLink class="nav-link" to="/wallet">{{ t.navWallet }}</RouterLink>
          <RouterLink class="nav-link" to="/kyc">{{ t.navKyc }}</RouterLink>
          <RouterLink class="nav-link" to="/login">{{ t.navLogin }}</RouterLink>
        </nav>
      </div>

      <div class="topbar-right">
        <button class="btn btn-ghost" @click="ui.toggleLang()">{{ ui.lang === "zh" ? "中文" : "EN" }}</button>
        <button class="btn btn-ghost" @click="ui.toggleTheme()">
          {{ ui.theme === "dark" ? t.light : t.dark }}
        </button>
        <span v-if="auth.me" class="badge">
          <span class="dot ok" />
          <span class="mono">{{ auth.me.email || auth.me.phone }}</span>
          <span class="muted">·</span>
          <span class="mono">{{ auth.me.kycStatus }}</span>
        </span>
        <button v-if="auth.token" class="btn btn-primary" @click="logout">{{ t.logout }}</button>
      </div>
    </header>

    <div style="margin-top: 14px">
      <RouterView />
    </div>
    <ToastHost />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRouter } from "vue-router";
import ToastHost from "./components/ToastHost.vue";
import { useAuthStore } from "./stores/auth";
import { http } from "./api/http";
import { useToastStore } from "./stores/toast";
import { useUiStore } from "./stores/ui";

const auth = useAuthStore();
const router = useRouter();
const toast = useToastStore();
const ui = useUiStore();

const dict = {
  zh: {
    navTrade: "交易",
    navWallet: "资产",
    navKyc: "KYC",
    navLogin: "登录",
    logout: "退出",
    dark: "深色",
    light: "浅色"
  },
  en: {
    navTrade: "Trade",
    navWallet: "Wallet",
    navKyc: "KYC",
    navLogin: "Login",
    logout: "Logout",
    dark: "Dark",
    light: "Light"
  }
};

const t = computed(() => (ui.lang === "en" ? dict.en : dict.zh));

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
  ui.init();
  loadMe().catch(() => {
    auth.clear();
    toast.push("Auth", ui.lang === "zh" ? "登录已失效，请重新登录" : "Session expired, please login again");
  });
});
</script>
