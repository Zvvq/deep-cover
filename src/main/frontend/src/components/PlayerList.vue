<script setup>
import { computed } from 'vue';
import { playerColorLabel, playerColorValue } from '../domain/playerColors.js';
import { playerNumberLabel, playerTypeLabel, playerAvatarNumber } from '../domain/labels.js';

const props = defineProps({
  players: { type: Array, default: () => [] },
  currentPlayerId: { type: String, default: null },
  showTypes: { type: Boolean, default: false },
});

const sortedPlayers = computed(() => [...props.players].sort((a, b) => Number(a.number || 999) - Number(b.number || 999)));
</script>

<template>
  <ul class="player-list">
    <li v-for="player in sortedPlayers" :key="player.id" class="player-item" :class="{ dead: player.alive === false }">
      <span class="player-dot" :style="{ backgroundColor: playerColorValue(player.color) || '#64748b' }">{{ playerAvatarNumber(player) }}</span>
      <span class="player-main">
        <span class="player-name">{{ playerNumberLabel(player) }}</span>
        <span class="player-meta">
          <span class="player-color-label">{{ playerColorLabel(player.color) }}</span>
          <span v-if="showTypes" class="player-type-label">{{ playerTypeLabel(player.type) }}</span>
        </span>
      </span>
      <span v-if="player.host" class="player-badge">房主</span>
      <span v-if="player.id === currentPlayerId" class="player-badge you">你</span>
      <span class="player-status-icon" :class="player.alive === false ? 'dead' : 'alive'">{{ player.alive === false ? '出局' : '存活' }}</span>
    </li>
  </ul>
</template>
