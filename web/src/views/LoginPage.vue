<template>
  <div style="display: grid; gap: 14px; grid-template-columns: 1fr 1fr">
    <section class="panel">
      <div class="panel-header">
        <div class="panel-title">登录</div>
        <span class="badge">
          <span class="dot warn" />
          <span class="muted">两步验证</span>
        </span>
      </div>
      <div class="panel-body">
        <div style="display: grid; gap: 10px">
          <label style="display: grid; gap: 6px">
            <div class="muted">账号（邮箱/手机号）</div>
            <input v-model="login.identifier" class="input" />
          </label>
          <label style="display: grid; gap: 6px">
            <div class="muted">密码</div>
            <input v-model="login.password" class="input" type="password" />
          </label>

          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
            <button class="btn btn-primary" @click="requestOtp" :disabled="loading">获取验证码</button>
            <span v-if="otpEcho" class="badge">
              <span class="dot ok" />
              <span class="mono">DEV OTP：{{ otpEcho }}</span>
            </span>
          </div>

          <label style="display: grid; gap: 6px">
            <div class="muted">验证码</div>
            <input v-model="login.otp" class="input" />
          </label>

          <button class="btn" style="font-weight: 800; padding: 12px 12px" @click="doLogin" :disabled="loading">
            {{ loading ? "登录中…" : "登录" }}
          </button>

          <div class="muted" style="font-size: 12px">
            测试账号：<span class="mono">alice@example.com</span> / <span class="mono">Passw0rd!</span>（已 KYC，资金密码
            <span class="mono">123456</span>）
          </div>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <div class="panel-title">注册</div>
      </div>
      <div class="panel-body">
        <div style="display: grid; gap: 10px">
          <label style="display: grid; gap: 6px">
            <div class="muted">邮箱</div>
            <input v-model="reg.email" class="input" />
          </label>
          <label style="display: grid; gap: 6px">
            <div class="muted">密码（至少 8 位）</div>
            <input v-model="reg.password" class="input" type="password" />
          </label>
          <button class="btn btn-primary" @click="doRegister" :disabled="loading">{{ loading ? "提交中…" : "注册" }}</button>
          <div v-if="regRes" class="badge" style="border-color: rgba(22, 163, 74, 0.28)">
            <span class="dot ok" />
            <span class="mono">注册成功：{{ regRes }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";
import { useToastStore } from "../stores/toast";

const router = useRouter();
const auth = useAuthStore();
const toast = useToastStore();

const loading = ref(false);
const err = ref("");
const otpEcho = ref("");
const regRes = ref("");

const login = reactive({
  identifier: "alice@example.com",
  password: "Passw0rd!",
  otp: ""
});

const reg = reactive({
  email: "",
  password: "Passw0rd!"
});

function apiErr(e: any): string {
  return e?.response?.data?.message ?? e?.message ?? "请求失败";
}

async function requestOtp() {
  otpEcho.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/public/auth/login/otp", {
      identifier: login.identifier,
      password: login.password
    });
    otpEcho.value = res.data.otp;
    login.otp = res.data.otp;
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Login", err.value);
  } finally {
    loading.value = false;
  }
}

async function doLogin() {
  loading.value = true;
  try {
    const res = await http.post("/api/public/auth/login", {
      identifier: login.identifier,
      otp: login.otp
    });
    auth.setToken(res.data.token);
    const meRes = await http.get("/api/account/me");
    auth.me = meRes.data;
    toast.push("Login", "登录成功");
    await router.push("/trade");
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Login", err.value);
  } finally {
    loading.value = false;
  }
}

async function doRegister() {
  regRes.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/public/auth/register", {
      email: reg.email,
      password: reg.password
    });
    regRes.value = res.data.userId;
    toast.push("Register", "注册成功");
  } catch (e: any) {
    err.value = apiErr(e);
    toast.push("Register", err.value);
  } finally {
    loading.value = false;
  }
}
</script>
