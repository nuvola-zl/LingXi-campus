<script setup lang="ts">
import DomainLayout from '@/components/layout/DomainLayout.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const menu = [
  { key: 'chat', label: 'AI 对话', icon: '💬', path: '/hr/chat' },
  { key: 'leave', label: '请假申请', icon: '📝', path: '/hr/leave' },
  { key: 'punch', label: '打卡补正', icon: '⏰', path: '/hr/punch' },
]
</script>

<template>
  <DomainLayout>
    <template #sidebar>
      <AppSidebar :items="menu" base-path="/hr" />
    </template>
    <RouterView />
  </DomainLayout>
</template>