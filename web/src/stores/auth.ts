import { defineStore } from "pinia";

type UserProfile = {
  id: string;
  email?: string | null;
  phone?: string | null;
  name?: string | null;
  kycStatus: string;
  status: string;
};

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem("token") ?? "",
    me: null as UserProfile | null
  }),
  actions: {
    setToken(token: string) {
      this.token = token;
      localStorage.setItem("token", token);
    },
    clear() {
      this.token = "";
      this.me = null;
      localStorage.removeItem("token");
    }
  }
});

