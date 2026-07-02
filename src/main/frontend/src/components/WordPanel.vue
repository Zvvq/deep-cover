<script setup>
import { computed, ref } from 'vue';
import { playerColorLabel, playerColorValue } from '../domain/playerColors.js';
import { playerNumberLabel } from '../domain/labels.js';

const props = defineProps({
  session: { type: Object, required: true },
});
const emit = defineEmits(['submitWordDescription']);
const draft = ref('');

const currentPlayer = computed(() => props.session.currentPlayers.find((player) => player.id === props.session.playerId));
const snapshot = computed(() => props.session.wordDescriptionSnapshot || { descriptions: [] });
const isMyTurn = computed(() => snapshot.value.currentPlayerId === props.session.playerId && !snapshot.value.roundComplete);
const numberSource = computed(() => props.session.currentPlayerWord || currentPlayer.value);
const colorSource = computed(() => props.session.currentPlayerWord || currentPlayer.value);

function submitWordDescription() {
  const content = draft.value.trim();
  if (!content || !isMyTurn.value || props.session.wordDescriptionSubmitting) return;
  emit('submitWordDescription', content);
  draft.value = '';
}
</script>

<template>
  <section class="word-panel">
    <header class="word-panel-header">
      <div>
        <h2>关键词卧底</h2>
        <p>只显示你的关键词，其他玩家关键词不会展示</p>
      </div>
      <span class="word-status-pill">描述阶段</span>
    </header>

    <div class="my-word-card">
      <div class="word-meta-row">
        <span class="word-meta-pill">{{ playerNumberLabel(numberSource) }}</span>
        <span class="word-meta-pill" :style="{ color: playerColorValue(colorSource?.color) || '' }">{{ playerColorLabel(colorSource?.color) }}</span>
      </div>
      <span class="word-value-label">你的关键词</span>
      <strong class="word-value">{{ session.currentPlayerWord?.word || (session.wordLoading ? '正在加载' : '未获取') }}</strong>
    </div>

    <div class="word-description-status">
      <span class="word-meta-pill">第 {{ snapshot.roundNumber || 1 }} 轮</span>
      <span class="word-current-turn">{{ snapshot.roundComplete ? '本轮描述已完成' : ('轮到玩家' + (snapshot.currentNumber || '?') + '号描述') }}</span>
    </div>

    <div class="word-description-list">
      <p v-if="!snapshot.descriptions || snapshot.descriptions.length === 0" class="word-description-empty">等待玩家提交描述</p>
      <article v-for="description in snapshot.descriptions" :key="description.playerId + description.createdAt + description.content" class="word-description-item">
        <span class="word-description-meta">玩家{{ description.playerNumber || description.number || '?' }}号</span>
        <p>{{ description.content }}</p>
      </article>
    </div>

    <form v-if="isMyTurn" class="word-description-form" autocomplete="off" @submit.prevent="submitWordDescription">
      <input v-model="draft" class="input" maxlength="200" placeholder="用一句话描述你的关键词" :disabled="session.wordDescriptionSubmitting" />
      <button class="btn btn-primary" type="submit" :disabled="!draft.trim() || session.wordDescriptionSubmitting">
        {{ session.wordDescriptionSubmitting ? '提交中...' : '提交描述' }}
      </button>
    </form>
    <p v-else class="hint-text">等待当前玩家描述</p>
  </section>
</template>
