<script setup>
import { useClipboard } from '../composables/useClipboard.js';

const props = defineProps({
  roomCode: { type: String, default: '' },
});
const emit = defineEmits(['copied']);
const { copyText } = useClipboard();

async function copyRoomCode() {
  try {
    const copied = await copyText(props.roomCode);
    emit('copied', copied ? '房间号已复制' : '复制失败，请手动复制', copied ? 'info' : 'error');
  } catch {
    emit('copied', '复制失败，请手动复制', 'error');
  }
}
</script>

<template>
  <div class="room-code-badge">
    <span class="room-code-label">房间</span>
    <strong class="room-code-value">{{ roomCode || '------' }}</strong>
    <button class="icon-button" type="button" title="复制房间号" aria-label="复制房间号" @click="copyRoomCode">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
    </button>
  </div>
</template>
