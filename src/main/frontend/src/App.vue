<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import HomeView from './components/HomeView.vue';
import WaitingRoomView from './components/WaitingRoomView.vue';
import GameView from './components/GameView.vue';
import StatusToast from './components/StatusToast.vue';
import { ROOM_STATUS } from './domain/gamePhases.js';
import { useGameFlow } from './composables/useGameFlow.js';
import { useGameTimer } from './composables/useGameTimer.js';
import { useRoomApi } from './composables/useRoomApi.js';
import { useRoomSession } from './composables/useRoomSession.js';
import { useRoomSocket } from './composables/useRoomSocket.js';

const { state, restore, setJoinedRoom, clearSession } = useRoomSession();
const api = useRoomApi(state);
const timer = useGameTimer(api);
const currentView = ref('home');
const toast = ref({ visible: false, message: '', tone: 'info' });
let toastTimer = null;

function showToast(message, tone = 'info') {
  window.clearTimeout(toastTimer);
  toast.value = { visible: true, message, tone };
  toastTimer = window.setTimeout(() => {
    toast.value = { visible: false, message: '', tone: 'info' };
  }, 2600);
}

function setView(view) {
  currentView.value = view;
}

const flow = useGameFlow(state, { api, timer, setView, showToast });
const socket = useRoomSocket({
  onEvent: flow.handleRoomEvent,
  onDisconnect: () => {
    if (state.roomCode && currentView.value !== 'home') showToast('实时连接已断开，正在重连', 'error');
  },
});

function connectRoomSocket() {
  if (state.roomCode) socket.connect(state.roomCode);
}

async function handleCreateRoom(gameMode) {
  const result = await api.createRoom(gameMode);
  setJoinedRoom(result);
  flow.applySnapshot(result.snapshot);
  connectRoomSocket();
}

async function handleJoinRoom(roomCode) {
  const result = await api.joinRoom(roomCode);
  setJoinedRoom(result);
  flow.applySnapshot(result.snapshot);
  connectRoomSocket();
}

async function handleStartRoom() {
  const snapshot = await api.startRoom(state.roomCode);
  flow.applySnapshot(snapshot);
}

async function handleLeaveRoom() {
  try {
    if (state.roomCode) await api.leaveRoom(state.roomCode);
  } finally {
    socket.disconnect();
    timer.stopTimerInterval();
    clearSession();
    currentView.value = 'home';
  }
}

function handleSendMessage(content) {
  try {
    socket.publishChat(state.roomCode, state.playerToken, content);
  } catch (error) {
    showToast(error.message || '实时连接建立后即可发送', 'error');
  }
}

async function handleCastVote(targetPlayerId) {
  await flow.castVote(targetPlayerId);
}

async function handleSubmitWordDescription(content) {
  await flow.submitWordDescription(content);
}

onMounted(async () => {
  restore();
  if (!state.roomCode || !state.playerToken) return;
  try {
    const snapshot = await api.snapshot(state.roomCode);
    flow.applySnapshot(snapshot);
    if (snapshot.status !== ROOM_STATUS.DESTROYED) connectRoomSocket();
  } catch {
    socket.disconnect();
    clearSession();
    currentView.value = 'home';
  }
});

onBeforeUnmount(() => {
  socket.disconnect();
  timer.stopTimerInterval();
  window.clearTimeout(toastTimer);
});
</script>

<template>
  <HomeView
    v-if="currentView === 'home'"
    @create-room="handleCreateRoom"
    @join-room="handleJoinRoom"
    @notify="showToast"
  />
  <WaitingRoomView
    v-else-if="currentView === 'waiting'"
    :session="state"
    @start-room="handleStartRoom"
    @leave-room="handleLeaveRoom"
    @notify="showToast"
  />
  <GameView
    v-else
    :session="state"
    :timer="timer.timerState"
    @send-message="handleSendMessage"
    @cast-vote="handleCastVote"
    @submit-word-description="handleSubmitWordDescription"
    @leave-room="handleLeaveRoom"
    @notify="showToast"
  />
  <StatusToast :toast="toast" />
</template>
