import { defineStore } from "pinia";

export type ThemeMode = "dark" | "light";
export type UiLang = "zh" | "en";

function readTheme(): ThemeMode {
  const v = localStorage.getItem("theme");
  return v === "light" ? "light" : "dark";
}

function readLang(): UiLang {
  const v = localStorage.getItem("lang");
  return v === "en" ? "en" : "zh";
}

export const useUiStore = defineStore("ui", {
  state: () => ({
    theme: readTheme() as ThemeMode,
    lang: readLang() as UiLang
  }),
  actions: {
    setTheme(theme: ThemeMode) {
      this.theme = theme;
      localStorage.setItem("theme", theme);
      document.documentElement.dataset.theme = theme;
    },
    toggleTheme() {
      this.setTheme(this.theme === "dark" ? "light" : "dark");
    },
    setLang(lang: UiLang) {
      this.lang = lang;
      localStorage.setItem("lang", lang);
    },
    toggleLang() {
      this.setLang(this.lang === "zh" ? "en" : "zh");
    },
    init() {
      document.documentElement.dataset.theme = this.theme;
    }
  }
});

