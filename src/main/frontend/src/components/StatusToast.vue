<script setup>
defineProps({
  toast: {
    type: Object,
    default: () => ({ visible: false, message: '', tone: 'info' }),
  },
});
</script>

<template>
  <div v-if="toast.visible" class="toast" :class="{ error: toast.tone === 'error' }" role="status">
    {{ toast.message }}
  </div>
</template>
