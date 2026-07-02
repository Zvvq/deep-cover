<script setup>
defineProps({
  topic: { type: Object, default: null },
});
</script>

<template>
  <section v-if="topic?.content" class="topic-panel">
    <span class="topic-label">当前话题</span>
    <p>{{ topic.content }}</p>
  </section>
</template>
