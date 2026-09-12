<script setup lang="ts">
import DomainLayout from '@/components/layout/DomainLayout.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const menu = [
  { key: 'chat', label: 'AI 对话', icon: '💬', path: '/qa/chat' },
]
</script>

<template>
  <DomainLayout>
    <template #sidebar>
      <AppSidebar :items="menu" base-path="/qa" />
    </template>
    <RouterView />
  </DomainLayout>
</template>