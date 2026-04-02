import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { routes } from "./router/routes";
import "./styles/global.css";

const app = createApp(App);
app.use(createPinia());

const router = createRouter({
  history: createWebHistory(),
  routes
});
app.use(router);

app.mount("#app");
