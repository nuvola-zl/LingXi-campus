<script setup lang="ts">
import DomainLayout from '@/components/layout/DomainLayout.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const menu = [
  { key: 'chat', label: 'AI 对话', icon: '💬', path: '/it/chat' },
  { key: 'tickets', label: '我的报修单', icon: '🎫', path: '/it/tickets' },
  { key: 'knowledge', label: '知识库', icon: '📚', path: '/it/knowledge' },
  { key: 'engineer', label: '报修单处理', icon: '🔧', path: '/it/engineer', requireEngineer: true },
  { key: 'catalog', label: '服务目录', icon: '📋', path: '/it/service-catalog', requireAdmin: true },
]
</script>

<template>
  <DomainLayout>
    <template #sidebar>
      <AppSidebar :items="menu" base-path="/it" />
    </template>
    <RouterView />
  </DomainLayout>
</template>