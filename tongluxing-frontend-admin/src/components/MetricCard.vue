<script setup>
defineProps({ label: String, value: String, trend: String, down: Boolean, icon: [Object, Function] })
</script>

<template>
  <div class="metric">
    <div class="metric-top"><span>{{ label }}</span><span class="metric-icon"><component :is="icon" /></span></div>
    <div class="metric-value">{{ value }}</div>
    <span class="trend" :class="{ down }">{{ trend }}</span>
  </div>
</template>
