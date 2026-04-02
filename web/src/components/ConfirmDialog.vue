<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="emit('cancel')">
      <div class="modal">
        <div class="modal-title">{{ title }}</div>
        <div class="muted" style="margin-top: 8px; line-height: 1.45">{{ message }}</div>
        <div class="modal-actions">
          <button class="btn" @click="emit('cancel')">{{ cancelText }}</button>
          <button class="btn btn-primary" @click="emit('confirm')">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  open: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
}>();

const emit = defineEmits<{
  (e: "confirm"): void;
  (e: "cancel"): void;
}>();

const confirmText = props.confirmText ?? "确认";
const cancelText = props.cancelText ?? "取消";
</script>

