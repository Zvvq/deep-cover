<script setup>
import { computed } from 'vue';

const props = defineProps({
  timer: { type: Object, required: true },
});

const badgeClass = computed(() => ({
  warning: props.timer.display !== '--:--' && !props.timer.expired && props.timer.display <= '00:30',
  danger: props.timer.display !== '--:--' && !props.timer.expired && props.timer.display <= '00:10',
  expired: props.timer.expired,
}));
</script>

<template>
  <div class="timer-badge" :class="badgeClass" aria-live="polite">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
    <span>{{ timer.display }}</span>
  </div>
</template>
