import type { RouteRecordRaw } from "vue-router";
import LoginPage from "../views/LoginPage.vue";
import KycPage from "../views/KycPage.vue";
import WalletPage from "../views/WalletPage.vue";
import TradePage from "../views/TradePage.vue";

export const routes: RouteRecordRaw[] = [
  { path: "/", redirect: "/trade" },
  { path: "/login", component: LoginPage },
  { path: "/kyc", component: KycPage },
  { path: "/wallet", component: WalletPage },
  { path: "/trade", component: TradePage }
];

