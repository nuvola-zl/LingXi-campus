<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const visible = ref(false)
const isLogin = ref(true)
const formRef = ref<any>()

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '', realName: '' })

function open() { visible.value = true; isLogin.value = true }
function close() { visible.value = false }

async function handleLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await userStore.login(loginForm.value)
    ElMessage.success('登录成功')
    close()
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  }
}

defineExpose({ open, close })
</script>

<template>
  <transition name="dialog-fade">
    <div v-if="visible" class="dialog-overlay" @click="close">
      <div class="dialog-card" @click.stop>
        <button class="close-btn" @click="close">✕</button>
        <div class="dialog-header">
          <h2>登录 Haze AI Hub</h2>
          <p>登录后即可使用 AI 智能助手</p>
        </div>
        <form @submit.prevent="handleLogin" class="dialog-form">
          <div class="form-item">
            <label>用户名</label>
            <input v-model="loginForm.username" type="text" placeholder="请输入用户名" autocomplete="username" />
          </div>
          <div class="form-item">
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          </div>
          <button type="submit" class="submit-btn">登 录</button>
        </form>
      </div>
    </div>
  </transition>
</template>

<style scoped lang="scss">
.dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center; z-index: 9999;
}
.dialog-card {
  position: relative; background: #fff; border-radius: 20px; padding: 2.5rem;
  max-width: 420px; width: 100%; box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  .dark & { background: #2a2a2a; }
}
.close-btn {
  position: absolute; top: 1rem; right: 1rem;
  width: 32px; height: 32px; border: none; background: rgba(0,0,0,0.05);
  border-radius: 50%; cursor: pointer; font-size: 1rem;
  .dark & { background: rgba(255,255,255,0.1); color: #ccc; }
}
.dialog-header {
  text-align: center; margin-bottom: 2rem;
  h2 { font-size: 1.5rem; margin-bottom: 0.5rem; color: #333; .dark & { color: #fff; } }
  p { color: #999; font-size: 0.875rem; }
}
.form-item {
  margin-bottom: 1.25rem;
  label { display: block; margin-bottom: 0.375rem; font-size: 0.875rem; color: #555; .dark & { color: #ccc; } }
  input {
    width: 100%; padding: 0.75rem 1rem; border: 1px solid #e5e7eb; border-radius: 0.5rem;
    font-size: 0.9375rem; outline: none; transition: border-color 0.2s; background: #f9fafb;
    .dark & { background: #333; border-color: #444; color: #fff; }
    &:focus { border-color: #007CF0; }
  }
}
.submit-btn {
  width: 100%; padding: 0.875rem; background: linear-gradient(135deg, #007CF0, #00DFD8);
  color: #fff; border: none; border-radius: 0.5rem; font-size: 1rem; font-weight: 600;
  cursor: pointer; transition: opacity 0.2s;
  &:hover { opacity: 0.9; }
}
.dialog-fade-enter-active, .dialog-fade-leave-active { transition: opacity 0.3s ease; }
.dialog-fade-enter-from, .dialog-fade-leave-to { opacity: 0; }
</style>