<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

interface MenuItem {
  key: string
  label: string
  icon: string
  path: string
  requireAdmin?: boolean
  requireEngineer?: boolean
}

const props = defineProps<{ items: MenuItem[]; basePath: string }>()

const filteredItems = computed(() =>
  props.items.filter((item) => {
    if (item.requireAdmin && !userStore.isAdmin) return false
    if (item.requireEngineer && !userStore.isEngineerOrAdmin) return false
    return true
  }),
)
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <span class="sidebar-title">{{ { '/it': 'IT 服务台', '/hr': 'HR 服务中心', '/admin': '行政服务', '/qa': '知识问答' }[basePath] || basePath }}</span>
    </div>
    <nav class="sidebar-nav">
      <router-link
        v-for="item in filteredItems"
        :key="item.key"
        :to="item.path"
        class="nav-item"
        :class="{ active: route.path === item.path }"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>
    <div class="sidebar-footer">
      <button class="home-btn" @click="router.push('/')">
        <span class="nav-icon">🏠</span>
        <span class="nav-label">返回首页</span>
      </button>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.sidebar {
  width: 220px; flex-shrink: 0;
  background: var(--card-bg);
  border-right: 1px solid var(--border-color);
  display: flex; flex-direction: column;
}
.sidebar-header {
  padding: 1.25rem 1rem; border-bottom: 1px solid var(--border-color);
  .sidebar-title { font-weight: 600; font-size: 0.9375rem; color: var(--text-color); }
}
.sidebar-nav {
  flex: 1; padding: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem;
}
.nav-item {
  display: flex; align-items: center; gap: 0.75rem;
  padding: 0.75rem 1rem; border-radius: 0.5rem;
  text-decoration: none; color: var(--text-color);
  font-size: 0.875rem; transition: all 0.2s;
  &:hover { background: rgba(0,124,240,0.08); }
  &.active { background: rgba(0,124,240,0.12); color: #007CF0; font-weight: 500; }
  .nav-icon { font-size: 1.1rem; width: 22px; text-align: center; }
}
.sidebar-footer {
  padding: 0.5rem; border-top: 1px solid var(--border-color);
  .home-btn {
    display: flex; align-items: center; gap: 0.75rem;
    width: 100%; padding: 0.75rem 1rem; border: none; border-radius: 0.5rem;
    background: transparent; color: var(--text-color);
    font-size: 0.875rem; cursor: pointer; transition: all 0.2s;
    &:hover { background: rgba(0,124,240,0.08); }
    .nav-icon { font-size: 1.1rem; width: 22px; text-align: center; }
  }
}
</style>