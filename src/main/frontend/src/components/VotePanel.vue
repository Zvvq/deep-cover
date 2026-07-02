<script setup>
import { computed } from 'vue';
import { playerColorLabel } from '../domain/playerColors.js';
import { playerNumberLabel } from '../domain/labels.js';

const props = defineProps({
  session: { type: Object, required: true },
  candidates: { type: Array, default: () => [] },
});
const emit = defineEmits(['castVote']);

const currentPlayer = computed(() => props.session.currentPlayers.find((player) => player.id === props.session.playerId));
const hasVoted = computed(() => !!props.session.voteSnapshot?.currentPlayerVoted);
const alive = computed(() => !currentPlayer.value || currentPlayer.value.alive !== false);
const progress = computed(() => {
  const snapshot = props.session.voteSnapshot;
  return snapshot ? '已提交 ' + snapshot.submittedVoteCount + '/' + snapshot.requiredVoteCount + ' 票' : '正在加载投票状态';
});

function castVote(targetPlayerId) {
  if (!targetPlayerId || hasVoted.value || props.session.voteSubmitting || !alive.value) return;
  emit('castVote', targetPlayerId);
}
</script>

<template>
  <section class="vote-panel">
    <header class="vote-panel-header">
      <div>
        <h2>投票</h2>
        <p>{{ progress }}</p>
      </div>
      <span class="vote-status-pill" :class="{ done: hasVoted }">{{ hasVoted ? '已提交' : (session.voteSubmitting ? '提交中' : '待投票') }}</span>
    </header>

    <div class="vote-candidates">
      <button
        v-for="player in candidates"
        :key="player.id"
        class="vote-candidate"
        type="button"
        :disabled="!alive || hasVoted || session.voteSubmitting"
        @click="castVote(player.id)"
      >
        <span class="vote-candidate-name">{{ playerNumberLabel(player) }}</span>
        <span class="vote-candidate-meta">{{ playerColorLabel(player.color) }}</span>
      </button>
    </div>

    <p class="vote-hint">
      <template v-if="!alive">你已出局，不能参与本轮投票</template>
      <template v-else-if="hasVoted">投票已提交，等待其他玩家完成投票</template>
      <template v-else-if="candidates.length === 0">暂无可投票对象</template>
      <template v-else>选择你认为最可疑的玩家</template>
    </p>
  </section>
</template>
