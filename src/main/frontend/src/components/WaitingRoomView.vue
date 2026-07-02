<script setup>
import { computed, ref } from 'vue';
import { gameModeLabel, roomStatusLabel } from '../domain/labels.js';
import PlayerList from './PlayerList.vue';
import RoomCodeBadge from './RoomCodeBadge.vue';

const props = defineProps({
  session: { type: Object, required: true },
});
const emit = defineEmits(['start-room', 'leave-room', 'notify']);
const starting = ref(false);
const leaving = ref(false);

const currentPlayer = computed(() => props.session.currentPlayers.find((player) => player.id === props.session.playerId));
const isHost = computed(() => !!currentPlayer.value?.host);
const humanPlayerCount = computed(() => props.session.currentPlayers.filter((player) => player.type !== 'AI').length);
const canStart = computed(() => isHost.value && humanPlayerCount.value >= 2 && !starting.value);

async function startRoom() {
  if (!canStart.value) return;
  starting.value = true;
  try {
    await emit('start-room');
  } catch (error) {
    emit('notify', error.message || '开始游戏失败', 'error');
  } finally {
    starting.value = false;
  }
}

async function leaveRoom() {
  if (leaving.value) return;
  leaving.value = true;
  try {
    await emit('leave-room');
  } catch (error) {
    emit('notify', error.message || '离开房间失败', 'error');
  } finally {
    leaving.value = false;
  }
}
</script>

<template>
  <main class="room-shell app-shell">
    <section class="room-card panel">
      <header class="room-header">
        <button class="btn btn-ghost" type="button" :disabled="leaving" @click="leaveRoom">退出</button>
        <RoomCodeBadge :room-code="session.roomCode" @copied="(...args) => emit('notify', ...args)" />
        <span class="phase-badge">{{ roomStatusLabel(session.currentRoomStatus) }}</span>
      </header>

      <div class="room-summary">
        <div>
          <p class="eyebrow">{{ gameModeLabel(session.currentGameMode) }}</p>
          <h1>等待玩家加入</h1>
        </div>
        <span class="count-badge">{{ session.currentPlayers.length }} 人</span>
      </div>

      <PlayerList :players="session.currentPlayers" :current-player-id="session.playerId" />
      <p v-if="humanPlayerCount < 2" class="hint-text">至少需要 2 名真人玩家才能开始游戏</p>

      <footer class="room-footer">
        <button v-if="isHost" class="btn btn-primary btn-block" type="button" :disabled="!canStart" @click="startRoom">
          {{ starting ? '启动中...' : '开始游戏' }}
        </button>
        <p v-else class="hint-text">等待房主开始游戏</p>
      </footer>
    </section>
  </main>
</template>
