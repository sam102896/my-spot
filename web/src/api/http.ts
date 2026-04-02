import axios from "axios";
import { useAuthStore } from "../stores/auth";

function getDeviceId(): string {
  const k = "deviceId";
  const existing = localStorage.getItem(k);
  if (existing) return existing;
  const v = crypto.randomUUID();
  localStorage.setItem(k, v);
  return v;
}

export const http = axios.create({
  baseURL: ""
});

http.interceptors.request.use((config) => {
  const auth = useAuthStore();
  config.headers = config.headers ?? {};
  config.headers["X-Device-Id"] = getDeviceId();
  if (auth.token) {
    config.headers["Authorization"] = `Bearer ${auth.token}`;
  }
  return config;
});

