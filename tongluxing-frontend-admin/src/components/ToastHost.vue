<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const message = ref('')
const visible = ref(false)
let timer

function handleToast(event) {
  message.value = event.detail
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 2200)
}

onMounted(() => window.addEventListener('admin-toast', handleToast))
onBeforeUnmount(() => {
  window.removeEventListener('admin-toast', handleToast)
  clearTimeout(timer)
})
</script>

<template><div class="toast" :class="{ show: visible }">{{ message }}</div></template>
