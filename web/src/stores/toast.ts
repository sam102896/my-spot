import { defineStore } from "pinia";

export type ToastItem = {
  id: string;
  title: string;
  message: string;
  createdAt: number;
};

function id(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export const useToastStore = defineStore("toast", {
  state: () => ({
    items: [] as ToastItem[]
  }),
  actions: {
    push(title: string, message: string, ttlMs = 3200) {
      const item: ToastItem = { id: id(), title, message, createdAt: Date.now() };
      this.items = [item, ...this.items].slice(0, 5);
      window.setTimeout(() => {
        this.items = this.items.filter((t) => t.id !== item.id);
      }, ttlMs);
    }
  }
});

