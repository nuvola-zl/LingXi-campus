<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { useDark, useToggle } from '@vueuse/core'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import LoginDialog from '@/components/common/LoginDialog.vue'
import { useUserStore } from '@/stores/user'

const isDark = useDark()
const toggleDark = useToggle(isDark)
const router = useRouter()
const userStore = useUserStore()

const loginDialogRef = ref<any>()

const avatarUrl = computed(() => {
  const avatar = (userStore.userInfo as any)?.avatar
  return typeof avatar === 'string' && avatar ? avatar : ''
})

const openLogin = () => {
  if (userStore.isLoggedIn) {
    router.push('/profile')
  } else {
    window.dispatchEvent(new CustomEvent('open-login-dialog'))
  }
}

const handleOpenLoginDialog = () => {
  if (loginDialogRef.value) loginDialogRef.value.open()
}

onMounted(async () => {
  window.addEventListener('open-login-dialog', handleOpenLoginDialog)
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch {
      console.warn('获取用户信息失败，但保留 token')
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('open-login-dialog', handleOpenLoginDialog)
})
</script>

<template>
  <div class="app" :class="{ dark: isDark }">
    <nav class="navbar">
      <div class="logo" @click="router.push('/')">
        <span class="logo-mark">✦</span> 灵犀校园
      </div>
      <div class="nav-right">
        <span v-if="userStore.isLoggedIn" class="user-name">{{ userStore.username }}</span>
        <div class="avatar-btn" @click="openLogin" :title="userStore.isLoggedIn ? '个人中心' : '登录'">
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" alt="avatar" />
          <span v-else class="avatar-fallback">灵</span>
        </div>
        <button @click="toggleDark()" class="theme-toggle">
          {{ isDark ? '☀' : '☾' }}
        </button>
        <button v-if="userStore.isLoggedIn" class="logout-btn" @click="userStore.logout(); router.push('/')">退出</button>
      </div>
    </nav>
    <RouterView v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </RouterView>
    <LoginDialog ref="loginDialogRef" />
  </div>
</template>

<style lang="scss">
:root {
  --bg-color: #f5f5f5;
  --text-color: #333;
  --card-bg: rgba(255, 255, 255, 0.95);
  --border-color: rgba(0, 0, 0, 0.08);
  /* 品牌主色（灵犀）：靛蓝 → 青 */
  --brand-from: #6366f1;
  --brand-to: #22d3ee;
}

.dark {
  --bg-color: #1a1a1a;
  --text-color: #e0e0e0;
  --card-bg: rgba(40, 40, 40, 0.95);
  --border-color: rgba(255, 255, 255, 0.08);
}

* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  color: var(--text-color);
  background: var(--bg-color);
  min-height: 100vh;
  transition: background-color 0.3s, color 0.3s;
}

.app { min-height: 100vh; display: flex; flex-direction: column; }

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 2rem;
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(10px);
  position: sticky; top: 0; z-index: 100;
  border-bottom: 1px solid var(--border-color);
  height: 56px;

  .logo {
    font-size: 1.15rem; font-weight: 700; cursor: pointer; letter-spacing: 0.02em;
    background: linear-gradient(45deg, var(--brand-from), var(--brand-to));
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
    display: flex; align-items: center; gap: 0.35rem;

    .logo-mark {
      font-size: 0.9rem;
      -webkit-text-fill-color: var(--brand-to);
    }
  }
  .nav-right {
    display: flex; align-items: center; gap: 0.75rem;
    .user-name { font-size: 0.875rem; color: var(--text-color); opacity: 0.8; }
  }
  .avatar-btn {
    width: 36px; height: 36px; border-radius: 50%;
    border: 1px solid var(--border-color);
    background: rgba(255,255,255,0.08);
    cursor: pointer; display: flex; align-items: center; justify-content: center;
    overflow: hidden; transition: background-color 0.2s;
    &:hover { background: rgba(255,255,255,0.15); }
  }
  .avatar-img { width: 100%; height: 100%; object-fit: cover; }
  .avatar-fallback {
    font-weight: 700; font-size: 13px;
    background: linear-gradient(45deg, var(--brand-from), var(--brand-to));
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  }
  .theme-toggle {
    background: none; border: none; font-size: 1.2rem; cursor: pointer;
    padding: 0.25rem; border-radius: 50%;
    &:hover { background: rgba(255,255,255,0.1); }
  }
  .logout-btn {
    background: rgba(255,77,79,0.1); color: #ff4d4f; border: 1px solid rgba(255,77,79,0.3);
    padding: 0.25rem 0.75rem; border-radius: 0.375rem; font-size: 0.8rem; cursor: pointer;
    &:hover { background: rgba(255,77,79,0.2); }
  }
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>