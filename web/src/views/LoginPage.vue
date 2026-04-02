<template>
  <div style="display: grid; gap: 16px; grid-template-columns: 1fr 1fr">
    <section style="border: 1px solid #ddd; padding: 12px">
      <h3>登录</h3>
      <div style="display: grid; gap: 8px">
        <label>
          账号（邮箱/手机号）
          <input v-model="login.identifier" style="width: 100%" />
        </label>
        <label>
          密码
          <input v-model="login.password" type="password" style="width: 100%" />
        </label>
        <div style="display: flex; gap: 8px; align-items: center">
          <button @click="requestOtp" :disabled="loading">获取验证码</button>
          <span v-if="otpEcho">DEV验证码：{{ otpEcho }}</span>
        </div>
        <label>
          验证码
          <input v-model="login.otp" style="width: 100%" />
        </label>
        <button @click="doLogin" :disabled="loading">登录</button>
        <div v-if="err" style="color: #c00">{{ err }}</div>
      </div>
      <div style="margin-top: 10px; font-size: 12px; color: #666">
        测试账号：alice@example.com / Passw0rd!（已KYC，已设置资金密码123456）
      </div>
    </section>

    <section style="border: 1px solid #ddd; padding: 12px">
      <h3>注册</h3>
      <div style="display: grid; gap: 8px">
        <label>
          邮箱
          <input v-model="reg.email" style="width: 100%" />
        </label>
        <label>
          密码（至少8位）
          <input v-model="reg.password" type="password" style="width: 100%" />
        </label>
        <button @click="doRegister" :disabled="loading">注册</button>
        <div v-if="regRes" style="color: #060">注册成功：{{ regRes }}</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { http } from "../api/http";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const auth = useAuthStore();

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
  err.value = "";
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
  } finally {
    loading.value = false;
  }
}

async function doLogin() {
  err.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/public/auth/login", {
      identifier: login.identifier,
      otp: login.otp
    });
    auth.setToken(res.data.token);
    const meRes = await http.get("/api/account/me");
    auth.me = meRes.data;
    await router.push("/trade");
  } catch (e: any) {
    err.value = apiErr(e);
  } finally {
    loading.value = false;
  }
}

async function doRegister() {
  err.value = "";
  regRes.value = "";
  loading.value = true;
  try {
    const res = await http.post("/api/public/auth/register", {
      email: reg.email,
      password: reg.password
    });
    regRes.value = res.data.userId;
  } catch (e: any) {
    err.value = apiErr(e);
  } finally {
    loading.value = false;
  }
}
</script>

