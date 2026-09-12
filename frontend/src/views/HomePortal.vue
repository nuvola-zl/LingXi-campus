<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

// 抽出一个方法
function openLoginDialog() {
  window.dispatchEvent(new CustomEvent('open-login-dialog'))
}

const entries = [
  { key: 'it', title: '智能报修', desc: '宿舍/教室设施故障，AI 辅助诊断并快速建单', icon: '🔧', color: '#6366f1' },
  { key: 'hr', title: '教务服务', desc: '请销假申请、课堂补签、教务政策咨询', icon: '📚', color: '#22d3ee' },
  { key: 'admin', title: '后勤服务', desc: '场地预约、器材借用、进度查询', icon: '🏟️', color: '#10b981' },
  { key: 'qa', title: '知识问答', desc: '校园办事指南智能问答，覆盖网务/教务/生活', icon: '🧠', color: '#f59e0b' },
]

function enter(key: string) {
  if (!userStore.isLoggedIn) {
    openLoginDialog()   // ← 用方法
    return
  }
  router.push(`/${key}`)
}
</script>

<template>
  <div class="home">
    <div class="hero">
      <h1 class="hero-title">灵犀校园</h1>
      <p class="hero-subtitle">校园智能服务门户，覆盖报修、教务、后勤、知识问答四大场景</p>
    </div>
    <div class="entries">
      <div
          v-for="entry in entries" :key="entry.key"
          class="entry-card"
          @click="enter(entry.key)"
      >
        <div class="entry-icon" :style="{ background: entry.color }">{{ entry.icon }}</div>
        <h2 class="entry-title">{{ entry.title }}</h2>
        <p class="entry-desc">{{ entry.desc }}</p>
        <button class="entry-btn" :style="{ borderColor: entry.color, color: entry.color }">
          进入
        </button>
      </div>
    </div>
    <div v-if="!userStore.isLoggedIn" class="login-hint">
      <p>请先登录以使用全部功能</p>
      <button class="login-btn" @click="openLoginDialog">
        立即登录
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  padding: 3rem 2rem;
}
.hero {
  text-align: center; margin-bottom: 3rem;
  .hero-title {
    font-size: 2.5rem; font-weight: 700; margin-bottom: 0.75rem; letter-spacing: 0.05em;
    background: linear-gradient(45deg, var(--brand-from), var(--brand-to));
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  }
  .hero-subtitle { color: #888; font-size: 1.0625rem; max-width: 500px; }
}
.entries {
  display: flex; gap: 1.5rem; flex-wrap: wrap; justify-content: center;
}
.entry-card {
  width: 280px; padding: 2rem; border-radius: 1rem;
  background: var(--card-bg); border: 1px solid var(--border-color);
  text-align: center; cursor: pointer;
  transition: all 0.3s ease;
  &:hover { transform: translateY(-4px); box-shadow: 0 12px 28px rgba(0,0,0,0.12); }
  .entry-icon {
    width: 64px; height: 64px; border-radius: 1rem;
    display: flex; align-items: center; justify-content: center;
    font-size: 1.75rem; margin: 0 auto 1rem;
  }
  .entry-title { font-size: 1.25rem; font-weight: 600; margin-bottom: 0.5rem; color: var(--text-color); }
  .entry-desc { color: #888; font-size: 0.875rem; margin-bottom: 1.25rem; line-height: 1.5; min-height: 42px; }
  .entry-btn {
    padding: 0.5rem 2rem; border: 2px solid; border-radius: 2rem;
    background: transparent; font-size: 0.9375rem; font-weight: 500; cursor: pointer;
    transition: all 0.2s;
    &:hover { opacity: 0.8; }
  }
}
.login-hint {
  margin-top: 2.5rem; text-align: center;
  p { color: #888; margin-bottom: 0.75rem; }
  .login-btn {
    padding: 0.625rem 2rem; border: none; border-radius: 2rem;
    background: linear-gradient(135deg, var(--brand-from), var(--brand-to));
    color: #fff; font-size: 0.9375rem; font-weight: 500; cursor: pointer;
    &:hover { opacity: 0.9; }
  }
}
</style>