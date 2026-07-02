<script setup>
import { computed, ref } from 'vue';
import { GAME_MODE } from '../domain/gamePhases.js';
import { gameModeLabel } from '../domain/labels.js';

const emit = defineEmits(['create-room', 'join-room', 'notify']);
const selectedGameMode = ref(GAME_MODE.CHAT_UNDERCOVER);
const roomCode = ref('');
const creating = ref(false);
const joining = ref(false);
const error = ref('');

const normalizedRoomCode = computed(() => roomCode.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6));
const canJoin = computed(() => normalizedRoomCode.value.length === 6 && !joining.value && !creating.value);

function normalizeRoomCode() {
  roomCode.value = normalizedRoomCode.value;
  error.value = '';
}

async function createRoom() {
  if (creating.value || joining.value) return;
  creating.value = true;
  error.value = '';
  try {
    await emit('create-room', selectedGameMode.value);
  } catch (err) {
    error.value = err.message || '创建房间失败';
    emit('notify', error.value, 'error');
  } finally {
    creating.value = false;
  }
}

async function joinRoom() {
  normalizeRoomCode();
  if (!canJoin.value) {
    error.value = '请输入 6 位房间号';
    return;
  }
  joining.value = true;
  error.value = '';
  try {
    await emit('join-room', normalizedRoomCode.value);
  } catch (err) {
    error.value = err.message || '加入房间失败';
    emit('notify', error.value, 'error');
  } finally {
    joining.value = false;
  }
}
</script>

<template>
  <main class="home-shell app-shell">
    <section class="home-card panel" aria-labelledby="home-title">
      <div class="home-heading">
        <p class="eyebrow">实时社交推理</p>
        <h1 id="home-title">Deep Cover</h1>
        <p>创建房间或输入房间号加入，找出隐藏在玩家中的 AI。</p>
      </div>

      <div class="mode-selector" role="radiogroup" aria-label="选择玩法">
        <label v-for="mode in [GAME_MODE.CHAT_UNDERCOVER, GAME_MODE.WORD_UNDERCOVER]" :key="mode" class="mode-option" :class="{ selected: selectedGameMode === mode }">
          <input v-model="selectedGameMode" type="radio" name="game-mode" :value="mode" />
          <span>{{ gameModeLabel(mode) }}</span>
        </label>
      </div>

      <button class="btn btn-primary btn-block" type="button" :disabled="creating || joining" @click="createRoom">
        <span aria-hidden="true">+</span>
        {{ creating ? '创建中...' : '创建房间' }}
      </button>

      <div class="divider"><span>或</span></div>

      <form class="join-form" autocomplete="off" @submit.prevent="joinRoom">
        <input v-model="roomCode" class="input room-code-input" maxlength="6" placeholder="输入房间号" autocomplete="off" @input="normalizeRoomCode" />
        <button class="btn btn-secondary" type="submit" :disabled="!canJoin">
          {{ joining ? '加入中...' : '加入房间' }}
        </button>
      </form>
      <p v-if="error" class="field-error">{{ error }}</p>
    </section>
  </main>
</template>
