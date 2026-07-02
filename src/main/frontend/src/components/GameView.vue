<script setup>
import { computed } from 'vue';
import { GAME_MODE, ROOM_STATUS } from '../domain/gamePhases.js';
import { roomStatusLabel } from '../domain/labels.js';
import ChatPanel from './ChatPanel.vue';
import PlayerList from './PlayerList.vue';
import RoomCodeBadge from './RoomCodeBadge.vue';
import TimerBadge from './TimerBadge.vue';
import TopicPanel from './TopicPanel.vue';
import VotePanel from './VotePanel.vue';
import WordPanel from './WordPanel.vue';

const props = defineProps({
  session: { type: Object, required: true },
  timer: { type: Object, required: true },
});
const emit = defineEmits(['send-message', 'cast-vote', 'submit-word-description', 'leave-room', 'notify']);

const currentPlayer = computed(() => props.session.currentPlayers.find((player) => player.id === props.session.playerId));
const alive = computed(() => !currentPlayer.value || currentPlayer.value.alive !== false);
const isChatting = computed(() => props.session.currentRoomStatus === ROOM_STATUS.CHATTING);
const chatDisabled = computed(() => !isChatting.value || !alive.value);
const chatPlaceholder = computed(() => {
  if (!alive.value) return '你已出局，无法发言';
  if (props.session.currentRoomStatus === ROOM_STATUS.VOTING) return '投票阶段暂停发言';
  if (props.session.currentRoomStatus === ROOM_STATUS.DESCRIBING) return '描述阶段暂未开放聊天';
  if (props.session.currentRoomStatus === ROOM_STATUS.ENDED) return '游戏已结束';
  return '输入消息...';
});
const voteCandidates = computed(() => {
  const ids = props.session.voteCandidateIds.length
    ? props.session.voteCandidateIds
    : props.session.currentPlayers.filter((player) => player.alive).map((player) => player.id);
  return ids
    .map((id) => props.session.currentPlayers.find((player) => player.id === id))
    .filter((player) => player && player.alive && player.id !== props.session.playerId);
});
const showWordPanel = computed(() => props.session.currentGameMode === GAME_MODE.WORD_UNDERCOVER && props.session.currentRoomStatus === ROOM_STATUS.DESCRIBING);
const showVotePanel = computed(() => props.session.currentRoomStatus === ROOM_STATUS.VOTING);
</script>

<template>
  <main class="game-layout">
    <header class="game-topbar">
      <button class="btn btn-ghost" type="button" @click="emit('leave-room')">退出</button>
      <RoomCodeBadge :room-code="session.roomCode" @copied="(...args) => emit('notify', ...args)" />
      <TimerBadge :timer="timer" />
      <span class="phase-badge" :class="{ expired: session.currentRoomStatus === ROOM_STATUS.ENDED }">{{ roomStatusLabel(session.currentRoomStatus) }}</span>
    </header>

    <section class="game-main">
      <div class="game-content">
        <TopicPanel :topic="session.currentTopic" />
        <WordPanel v-if="showWordPanel" :session="session" @submit-word-description="(content) => emit('submit-word-description', content)" />
        <VotePanel v-if="showVotePanel" :session="session" :candidates="voteCandidates" @cast-vote="(target) => emit('cast-vote', target)" />
        <ChatPanel
          :messages="session.messages"
          :players="session.currentPlayers"
          :current-player-id="session.playerId"
          :disabled="chatDisabled"
          :placeholder="chatPlaceholder"
          @send-message="(content) => emit('send-message', content)"
        />
      </div>

      <aside class="game-sidebar panel">
        <h2>玩家 <span class="count-badge">{{ session.currentPlayers.length }}</span></h2>
        <PlayerList :players="session.currentPlayers" :current-player-id="session.playerId" :show-types="session.currentGameMode === GAME_MODE.WORD_UNDERCOVER" />
      </aside>
    </section>
  </main>
</template>
