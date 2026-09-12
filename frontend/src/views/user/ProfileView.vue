<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const user = userStore.userInfo as any
</script>

<template>
  <div class="page">
    <div class="card">
      <h1>个人中心</h1>
      <div class="info-grid">
        <div class="info-item"><label>用户名</label><span>{{ user?.username || '-' }}</span></div>
        <div class="info-item"><label>姓名</label><span>{{ user?.realName || '-' }}</span></div>
        <div class="info-item"><label>邮箱</label><span>{{ user?.email || '-' }}</span></div>
        <div class="info-item"><label>手机</label><span>{{ user?.phone || '-' }}</span></div>
        <div class="info-item"><label>角色</label><span class="role">{{ user?.roleType || 'EMPLOYEE' }}</span></div>
      </div>
      <button class="logout-btn" @click="userStore.logout(); router.push('/')">退出登录</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; display: flex; align-items: flex-start; justify-content: center; padding: 3rem 2rem; }
.card { width: 100%; max-width: 480px; padding: 2rem; background: var(--card-bg); border-radius: 1rem; border: 1px solid var(--border-color); h1 { margin-bottom: 1.5rem; } }
.info-grid { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1.5rem; }
.info-item { display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border-color); label { color: #888; font-size: 0.875rem; } span { font-weight: 500; } .role { color: #007CF0; } }
.logout-btn { width: 100%; padding: 0.75rem; background: rgba(255,77,79,0.1); color: #ff4d4f; border: 1px solid rgba(255,77,79,0.3); border-radius: 0.5rem; cursor: pointer; font-size: 1rem; &:hover { background: rgba(255,77,79,0.2); } }
</style>