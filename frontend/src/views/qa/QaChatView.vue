<script setup lang="ts">
import { ref, nextTick } from 'vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { getToken } from '@/utils/auth'
import { getApiBaseURL } from '@/utils/apiConfig'
import { ElMessage } from 'element-plus'

// ⚠️ 与数据库 kb_library 保持一致；长期应改为调用 GET /astra/libraries 动态获取
const libraries = [
  { id: 1, name: '校园办事指南', desc: '校园网/教务系统/报修渠道/奖学金', icon: '📖', color: '#6366f1' },
  { id: 2, name: 'HR知识库',     desc: '教务政策、请销假、奖助制度（待建设）', icon: '📚', color: '#22d3ee' },
  { id: 3, name: '行政知识库',   desc: '场地器材、后勤服务（待建设）',   icon: '🏟️', color: '#10b981' },
  { id: 4, name: '财务知识库',   desc: '缴费报销、财务制度（待建设）', icon: '💰', color: '#f59e0b' },
]

// 默认选中主库（校园办事指南），减少一步操作
const selectedId = ref<number | null>(1)
const answerStyle = ref<'detail' | 'concise'>('detail')
const messages = ref<any[]>([])
const isStreaming = ref(false)
const abortCtrl = ref<AbortController | null>(null)
const messagesRef = ref<HTMLElement>()
const currentSessionId = ref<number | null>(null)

const selectedLib = ref<any>(libraries[0])

function selectLib(lib: any) {
  if (selectedId.value === lib.id) return
  selectedId.value = lib.id
  selectedLib.value = lib
  currentSessionId.value = null
  messages.value = []
}

function getPlaceholder() {
  if (!selectedLib.value) return '请先在上方选择一个知识库'
  return `向「${selectedLib.value.name}」提问...`
}

async function sendMessage(data: { text: string; file: File | null }) {
  if (isStreaming.value || !selectedId.value) {
    if (!selectedId.value) ElMessage.warning('请先选择一个知识库')
    return
  }

  const prompt = data.text
  messages.value.push({ role: 'user', content: prompt, timestamp: new Date() })
  messages.value.push({ role: 'assistant', content: '', timestamp: new Date() })
  const aiIdx = messages.value.length - 1
  scrollToBottom()
  isStreaming.value = true

  const ctrl = new AbortController()
  abortCtrl.value = ctrl

  try {
    const BASE_URL = getApiBaseURL()
    const token = getToken()
    const response = await fetch(`${BASE_URL}/astra/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token || '',
      },
      body: JSON.stringify({
        libraryId: selectedId.value,
        prompt,
        sessionId: currentSessionId.value,
        style: answerStyle.value,
      }),
      signal: ctrl.signal,
    })

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)

    const reader = response.body!.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })

      while (true) {
        const idx = buffer.indexOf('\n\n')
        if (idx === -1) break
        const block = buffer.substring(0, idx)
        buffer = buffer.substring(idx + 2)

        let eventType = ''
        let eventData = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) eventType = line.substring(6).trim()
          else if (line.startsWith('data:')) eventData = line.substring(5)
        }
        if (!eventData) continue

        if (eventType === 'session-created') {
          try {
            currentSessionId.value = JSON.parse(eventData).sessionId
          } catch { /* ignore */ }
        } else if (eventType === 'answer') {
          messages.value[aiIdx].content += eventData
          scrollToBottom()
        } else if (eventType === 'thinking') {
          messages.value[aiIdx].thinking = eventData
        }
      }
      if (done) break
    }
  } catch (e: any) {
    if (e.name === 'AbortError') {
      if (!messages.value[aiIdx].content) messages.value[aiIdx].content = '已停止生成'
    } else {
      messages.value[aiIdx].content = messages.value[aiIdx].content || e.message || '请求失败'
      messages.value[aiIdx].role = 'error'
    }
  } finally {
    isStreaming.value = false
    abortCtrl.value = null
  }
}

function stopStreaming() {
  abortCtrl.value?.abort()
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}
</script>

<template>
  <div style="display:flex; flex:1; height:100%; overflow:hidden">
    <div class="chat-main">
      <!-- 知识库卡片选择区 -->
      <div class="library-bar">
        <span class="bar-label">选择知识库：</span>
        <div class="library-cards">
          <div
              v-for="lib in libraries"
              :key="lib.id"
              class="lib-card"
              :class="{ active: selectedId === lib.id }"
              :style="{ borderColor: selectedId === lib.id ? lib.color : undefined }"
              @click="selectLib(lib)"
          >
            <span class="lib-icon">{{ lib.icon }}</span>
            <div>
              <div class="lib-name">{{ lib.name }}</div>
              <div class="lib-desc">{{ lib.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 回答风格切换 -->
      <div class="style-bar">
        <span class="bar-label">回答风格：</span>
        <div class="style-toggle">
          <button
              class="style-btn"
              :class="{ active: answerStyle === 'detail' }"
              @click="answerStyle = 'detail'"
          >详细</button>
          <button
              class="style-btn"
              :class="{ active: answerStyle === 'concise' }"
              @click="answerStyle = 'concise'"
          >简洁</button>
        </div>
        <span class="style-hint">{{ answerStyle === 'detail' ? '完整引用原文，适合查阅办事指南' : '提炼关键步骤，适合快速排查问题' }}</span>
      </div>

      <!-- 消息区 -->
      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="welcome">
          <h2>知识问答</h2>
          <p v-if="!selectedId">请在上方选择一个知识库，然后开始提问</p>
          <p v-else>向「{{ selectedLib?.name }}」提问，获取智能回答</p>
        </div>
        <ChatMessage v-for="(m, i) in messages" :key="i" :message="m"
                     :is-stream="isStreaming && i === messages.length - 1" />
      </div>

      <!-- 输入区 -->
      <ChatInput ref="chatInputRef" :isStreaming="isStreaming"
                 :placeholder="getPlaceholder()" @send="sendMessage" @stop="stopStreaming" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.library-bar {
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  display: flex; align-items: flex-start; gap: 0.75rem;
  .bar-label {
    font-size: 0.8125rem; color: #888; white-space: nowrap;
    padding-top: 0.5rem;
  }
}
.library-cards {
  display: flex; gap: 0.625rem; flex-wrap: wrap;
}
.lib-card {
  display: flex; align-items: center; gap: 0.625rem;
  padding: 0.625rem 0.875rem;
  border: 1.5px solid var(--border-color);
  border-radius: 0.5rem; cursor: pointer;
  min-width: 200px;
  transition: all 0.2s;
  background: var(--card-bg);
  &:hover { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
  &.active {
    background: var(--card-bg);
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  }
  .lib-icon { font-size: 1.25rem; flex-shrink: 0; }
  .lib-name { font-size: 0.875rem; font-weight: 600; color: var(--text-color); }
  .lib-desc { font-size: 0.75rem; color: #888; margin-top: 0.125rem; }
}

.style-bar {
  padding: 0.5rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  display: flex; align-items: center; gap: 0.75rem;
  .bar-label {
    font-size: 0.8125rem; color: #888; white-space: nowrap;
  }
}
.style-toggle {
  display: flex; border: 1px solid var(--border-color); border-radius: 0.375rem; overflow: hidden;
}
.style-btn {
  padding: 0.25rem 0.75rem; border: none; background: transparent;
  font-size: 0.8125rem; color: #888; cursor: pointer; transition: all 0.15s;
  &:hover { background: rgba(0,0,0,0.04); }
  &.active {
    background: var(--brand-from); color: #fff;
  }
}
.style-hint {
  font-size: 0.75rem; color: #aaa;
}

.messages {
  flex: 1; overflow-y: auto; padding: 1.5rem;
  .welcome { text-align: center; padding: 3rem 0; color: #888;
    h2 { color: var(--text-color); margin-bottom: 0.5rem; }
  }
}
</style>