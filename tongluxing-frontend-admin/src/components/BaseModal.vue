<script setup>
import { X } from 'lucide-vue-next'

defineProps({
  open: Boolean,
  title: { type: String, default: '详情' },
  size: { type: String, default: 'default' },
})
const emit = defineEmits(['close'])
</script>

<template>
  <div class="modal-mask" :class="{ show: open }" @click.self="emit('close')">
    <div class="modal" :class="`modal-${size}`">
      <div class="modal-head">
        <h3>{{ title }}</h3>
        <X class="close-modal" @click="emit('close')" />
      </div>
      <div class="modal-body"><slot /></div>
      <div class="modal-foot"><slot name="footer" /></div>
    </div>
  </div>
</template>
