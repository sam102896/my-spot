<template>
  <div class="toast-wrap">
    <div v-for="t in items" :key="t.id" class="toast">
      <div class="toast-title">{{ t.title }}</div>
      <div class="toast-msg">{{ t.message }}</div>
      <div class="toast-line"><div /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useToastStore } from "../stores/toast";

const toast = useToastStore();
const items = computed(() => toast.items);
</script>

