<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { playerColorValue } from '../domain/playerColors.js';
import { formatISOTime, playerAvatarNumber, playerNumberLabel } from '../domain/labels.js';

const props = defineProps({
  messages: { type: Array, default: () => [] },
  players: { type: Array, default: () => [] },
  currentPlayerId: { type: String, default: null },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '输入消息...' },
});
const emit = defineEmits(['send-message']);
const draft = ref('');
const messagesEl = ref(null);

const canSend = computed(() => draft.value.trim().length > 0 && !props.disabled);

function playerById(playerId) {
  return props.players.find((player) => player.id === playerId) || null;
}

function submit() {
  const content = draft.value.trim();
  if (!content || props.disabled) return;
  emit('send-message', content);
  draft.value = '';
}

watch(() => props.messages.length, async () => {
  await nextTick();
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
});
</script>

<template>
  <section class="chat-panel">
    <div ref="messagesEl" class="chat-messages">
      <p v-if="messages.length === 0" class="chat-placeholder">游戏开始，讨论吧...</p>
      <div
        v-for="message in messages"
        :key="message.id || message.createdAt || message.content"
        class="chat-msg"
        :class="{ self: message.senderPlayerId === currentPlayerId, event: message.system }"
      >
        <template v-if="message.system">
          {{ message.content }}
        </template>
        <template v-else>
          <div class="chat-avatar" :style="{ backgroundColor: playerColorValue(playerById(message.senderPlayerId)?.color) || '#64748b' }">
            {{ playerAvatarNumber(playerById(message.senderPlayerId)) }}
          </div>
          <div class="chat-msg-body">
            <div class="chat-msg-header">
              <span class="chat-msg-sender">{{ message.senderPlayerId === currentPlayerId ? '你 · ' + playerNumberLabel(playerById(message.senderPlayerId)) : playerNumberLabel(playerById(message.senderPlayerId)) }}</span>
              <time>{{ formatISOTime(message.createdAt) }}</time>
            </div>
            <p class="chat-msg-content">{{ message.content }}</p>
          </div>
        </template>
      </div>
    </div>

    <form class="chat-input-bar" autocomplete="off" @submit.prevent="submit">
      <input v-model="draft" class="input chat-input" :placeholder="placeholder" maxlength="300" :disabled="disabled" />
      <button class="btn btn-primary icon-send" type="submit" :disabled="!canSend" title="发送">发送</button>
    </form>
  </section>
</template>
