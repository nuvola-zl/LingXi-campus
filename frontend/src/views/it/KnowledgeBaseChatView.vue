<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getLibrary, uploadMedia, getMediaList, deleteMedia, chatWithLibrary, subscribeParseProgress } from '@/api/knowledge'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()
const libraryId = Number(route.params.id)
const library = ref<any>(null)
const mediaList = ref<any[]>([])
const messages = ref<any[]>([])
const isStreaming = ref(false)
const userInput = ref('')
const messagesRef = ref<HTMLElement>()
const parseTasks = reactive<Record<number, { percent: number; status: string; abortCtrl?: AbortController }>>({})

// 解析状态文案映射（提取为具名常量并声明类型，修复模板内索引的 TS 报错）
const STATUS_TEXT: Record<string, string> = {
  PENDING: '等待中',
  PARSING: '解析中',
  PARSED: '已完成',
  FAILED: '失败',
}

async function loadData() {
  const libRes = await getLibrary(libraryId)
  library.value = libRes.data || libRes
  const mediaRes = await getMediaList(libraryId)
  mediaList.value = mediaRes.data || []
}

async function handleUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    const res = await uploadMedia(file, libraryId)
    const mediaId = res?.data?.id
    if (!mediaId) { ElMessage.error('上传返回异常'); return }
    ElMessage.success('上传成功，正在解析中...')

    const abortCtrl = new AbortController()
    parseTasks[mediaId] = { percent: 0, status: 'PENDING', abortCtrl }
    loadData()

    subscribeParseProgress(mediaId,
        (data) => { parseTasks[mediaId] = { ...parseTasks[mediaId], percent: data.percent, status: 'PARSING' } },
        (data) => {
          parseTasks[mediaId] = { percent: 100, status: 'PARSED' }
          setTimeout(() => { delete parseTasks[mediaId]; loadData() }, 2000)
        },
        (error) => {
          parseTasks[mediaId] = { percent: 0, status: 'FAILED' }
          ElMessage.error(`解析失败: ${error}`)
          setTimeout(() => { delete parseTasks[mediaId]; loadData() }, 3000)
        },
        abortCtrl.signal,
    ).catch(() => { /* aborted */ })
  } catch { ElMessage.error('上传失败') }
}

onUnmounted(() => {
  Object.values(parseTasks).forEach(t => t.abortCtrl?.abort())
})

async function handleDeleteMedia(id: number) {
  await deleteMedia(id)
  ElMessage.success('已删除')
  loadData()
}

async function sendMessage() {
  if (isStreaming.value || !userInput.value.trim()) return
  const prompt = userInput.value.trim()
  userInput.value = ''

  messages.value.push({ role: 'user', content: prompt })
  const assistant = { role: 'assistant', content: '' }
  messages.value.push(assistant)
  isStreaming.value = true

  try {
    const reader = await chatWithLibrary(libraryId, prompt)
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const lines = buffer.split('\n')
      buffer = done ? '' : (lines.pop() || '')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          assistant.content += line.substring(5)
        }
      }
      if (done) break
      scrollToBottom()
    }
  } catch (e: any) {
    assistant.content = e.message || '请求失败'
  } finally {
    isStreaming.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <router-link to="/it/knowledge">← 返回知识库</router-link>
      <h1>{{ library?.name }}</h1>
    </div>

    <div v-if="userStore.isAdmin" class="upload-section">
      <label class="upload-btn">上传文档<input type="file" @change="handleUpload" style="display:none" accept=".pdf,.docx,.md,.txt" /></label>
      <div v-for="m in mediaList" :key="m.id" class="media-item">
        <span>{{ m.fileName }}</span>
        <span class="media-status" :class="'status-' + (m.status || '').toLowerCase()">
          {{ STATUS_TEXT[m.status] || m.status }}
        </span>
        <button @click="handleDeleteMedia(m.id)">✕</button>
      </div>
      <div v-for="(task, mediaId) in parseTasks" :key="'parse-' + mediaId" class="parse-progress">
        <span class="progress-label">解析进度</span>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: task.percent + '%' }" :class="task.status.toLowerCase()"></div>
        </div>
        <span class="progress-text">{{ task.percent }}%</span>
      </div>
    </div>

    <div class="chat-section">
      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="welcome"><p>向知识库 "{{ library?.name }}" 提问</p></div>
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="msg-content">{{ m.content }}</div>
        </div>
      </div>
      <div class="input-row">
        <input v-model="userInput" @keydown.enter="sendMessage" placeholder="向知识库提问..." :disabled="isStreaming" />
        <button @click="sendMessage" :disabled="isStreaming || !userInput.trim()">发送</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; display: flex; flex-direction: column; }
.page-header { margin-bottom: 1rem; a { color: var(--brand-from); text-decoration: none; font-size: 0.875rem; } h1 { font-size: 1.25rem; margin-top: 0.5rem; } }
.upload-section { margin-bottom: 1rem; .upload-btn { display: inline-block; padding: 0.5rem 1rem; background: var(--brand-from); color: #fff; border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; } }
.media-item { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem; margin-top: 0.25rem; font-size: 0.8125rem; background: var(--card-bg); border-radius: 0.375rem;
  .media-status { font-size: 0.75rem; &.status-parsed { color: #10b981; } &.status-parsing { color: #f59e0b; } &.status-failed { color: #ff4d4f; } &.status-pending { color: #888; } }
  button { background: none; border: none; color: #ff4d4f; cursor: pointer; }
}
.parse-progress { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem; margin-top: 0.25rem; font-size: 0.8125rem; background: var(--card-bg); border-radius: 0.375rem;
  .progress-label { color: #888; white-space: nowrap; }
  .progress-bar { flex: 1; height: 6px; background: rgba(0,0,0,0.06); border-radius: 3px; overflow: hidden;
    .progress-fill { height: 100%; border-radius: 3px; transition: width 0.4s ease; background: var(--brand-from);
      &.parsing { background: #f59e0b; } &.parsed { background: #10b981; } &.failed { background: #ff4d4f; }
    }
  }
  .progress-text { color: #888; font-size: 0.75rem; min-width: 2rem; text-align: right; }
}
.chat-section { flex: 1; display: flex; flex-direction: column; border: 1px solid var(--border-color); border-radius: 0.75rem; overflow: hidden; background: var(--card-bg); }
.messages { flex: 1; overflow-y: auto; padding: 1rem; .welcome { text-align: center; color: #888; padding: 3rem 0; } }
.msg { margin-bottom: 0.75rem; .msg-content { padding: 0.625rem 0.875rem; border-radius: 0.5rem; font-size: 0.9375rem; white-space: pre-wrap; } &.user { text-align: right; .msg-content { background: var(--brand-from); color: #fff; display: inline-block; } } &.assistant { .msg-content { background: rgba(0,0,0,0.04); } } }
.input-row { display: flex; padding: 0.75rem; border-top: 1px solid var(--border-color);
  input { flex: 1; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); outline: none; }
  button { padding: 0.5rem 1rem; background: var(--brand-from); color: #fff; border: none; border-radius: 0.375rem; margin-left: 0.5rem; cursor: pointer; &:disabled { background: #ccc; } }
}
</style>