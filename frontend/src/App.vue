<script setup lang="ts">
/**
 * 根组件 — 渲染当前路由页面 + 全局消息提示
 */
import ToastMessage from '@/components/ToastMessage.vue'
import FloatingAi from '@/components/FloatingAi.vue'
</script>

<template>
  <router-view />
  <ToastMessage />
  <FloatingAi />
</template>