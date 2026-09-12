<script setup lang="ts">
import DomainLayout from '@/components/layout/DomainLayout.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const menu = [
  { key: 'chat', label: 'AI 对话', icon: '💬', path: '/admin/chat' },
  { key: 'device', label: '器材借用', icon: '📷', path: '/admin/device' },
  { key: 'meeting', label: '场地预约', icon: '📅', path: '/admin/meeting' },
  { key: 'manage', label: '审批管理', icon: '✅', path: '/admin/manage', requireAdmin: true },
  { key: 'devices', label: '器材管理', icon: '📦', path: '/admin/devices', requireAdmin: true },
  { key: 'models', label: '模型管理', icon: '🤖', path: '/admin/models', requireAdmin: true },
]
</script>

<template>
  <DomainLayout>
    <template #sidebar>
      <AppSidebar :items="menu" base-path="/admin" />
    </template>
    <RouterView />
  </DomainLayout>
</template>