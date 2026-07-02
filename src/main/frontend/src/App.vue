<script setup>
import HomeView from './components/HomeView.vue';
import WaitingRoomView from './components/WaitingRoomView.vue';
import GameView from './components/GameView.vue';
import StatusToast from './components/StatusToast.vue';
</script>

<template>
  <HomeView />
  <WaitingRoomView hidden />
  <GameView hidden />
  <StatusToast />
</template>
