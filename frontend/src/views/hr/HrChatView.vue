<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import { chatAPI } from '@/api/chat'

const sessions = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<any[]>([])
const isStreaming = ref(false)
const abortCtrl = ref<AbortController | null>(null)
const messagesRef = ref<HTMLElement>()

const selectedModel = ref({ id: 'default', name: 'qwen-flash', value: 'qwen-flash' })
const selectedModelId = ref<string | number>('default')
const availableModels = ref<any[]>([])
const enableThinking = ref(true)

watch(selectedModelId, (newId) => {
  const found = availableModels.value.find((m: any) => m.id === newId)
  if (found) selectedModel.value = found
})

async function loadSessions() {
  sessions.value = await chatAPI.getChatHistory('HR')
}

async function loadModels() {
  try {
    const models = await chatAPI.getModelList()
    if (models.length > 0) {
      availableModels.value = models.filter((m: any) => m.status !== false)
      const rec = availableModels.value.find((m: any) => m.recommended)
      selectedModel.value = rec || availableModels.value[0] || selectedModel.value
      selectedModelId.value = selectedModel.value.id
    } else {
      console.warn('[HrChat] models list is empty')
    }
  } catch (e) {
    console.error('[HrChat] loadModels error:', e)
  }
}

async function selectSession(id: number) {
  currentSessionId.value = id
  messages.value = await chatAPI.getChatMessages(id)
  scrollToBottom()
}

function newChat() { currentSessionId.value = null; messages.value = [] }

async function sendMessage(data: { text: string; file: File | null }) {
  if (isStreaming.value) return
  const formData = new FormData()
  formData.append('domain', 'HR')
  formData.append('prompt', data.text)
  formData.append('model', selectedModel.value.name)
  formData.append('enableThinking', String(enableThinking.value))
  if (data.file) formData.append('file', data.file)

  messages.value.push({ role: 'user', content: data.text, timestamp: new Date() })
  messages.value.push({ role: 'assistant', content: '', timestamp: new Date(), metadata: {} })
  const aiIdx = messages.value.length - 1
  scrollToBottom()
  isStreaming.value = true

  const ctrl = new AbortController()
  abortCtrl.value = ctrl

  try {
    for await (const event of chatAPI.sendMessage(formData, currentSessionId.value, (newId) => {
      currentSessionId.value = Number(newId)
      loadSessions()
    }, ctrl.signal)) {
      if (event.type === 'ai_prompt') { messages.value[aiIdx].prompt = event.text }
      else if (event.type === 'image_url') { messages.value[aiIdx].type = 'image'; messages.value[aiIdx].imageUrl = event.text }
      else if (event.type === 'done') { /* ignore */ }
      else if (event.type === 'error') { throw new Error(event.error) }
      else if (event.type === 'content' && event.text) {
        messages.value[aiIdx].content += event.text
        scrollToBottom()
      }
    }
  } catch (e: any) {
    if (e.name === 'AbortError') {
      if (!messages.value[aiIdx].content) messages.value[aiIdx].content = '已停止生成'
    } else {
      messages.value[aiIdx].content = messages.value[aiIdx].content || e.message || '服务不可用'
      messages.value[aiIdx].role = 'error'
    }
  } finally {
    isStreaming.value = false
    abortCtrl.value = null
    if (currentSessionId.value) setTimeout(() => loadSessions(), 2000)
  }
}

function stopStreaming() {
  if (abortCtrl.value) {
    abortCtrl.value.abort()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

onMounted(() => { loadSessions(); loadModels() })
</script>

<template>
  <div style="display:flex; flex:1; height:100%; overflow:hidden">
    <ChatSidebar :sessions="sessions" :currentSessionId="currentSessionId"
                 @select="selectSession" @newChat="newChat" @refresh="loadSessions" />
    <div class="chat-main">
      <div class="domain-bar"><span class="tag">教务服务</span><select v-model="selectedModelId" class="model-select"><option v-for="m in availableModels" :key="m.id" :value="m.id">{{ m.name }}</option></select><label class="thinking-toggle"><input type="checkbox" v-model="enableThinking" /> 深度思考</label></div>
      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="welcome"><h2>灵犀教务助手</h2><p>请销假申请、课堂补签、成绩与政策咨询，一句话办理</p></div>
        <ChatMessage v-for="(m, i) in messages" :key="i" :message="m"
                     :is-stream="isStreaming && i === messages.length - 1" />
      </div>
      <ChatInput :isStreaming="isStreaming" placeholder="如：我要请 2 天病假，明天开始..." @send="sendMessage" @stop="stopStreaming" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.domain-bar { padding: 0.625rem 1.5rem; border-bottom: 1px solid var(--border-color); display: flex; align-items: center; gap: 0.75rem; .tag { padding: 0.125rem 0.5rem; background: rgba(99,102,241,0.1); color: var(--brand-from); border-radius: 0.25rem; font-size: 0.75rem; font-weight: 500; } .model-select { font-size: 0.8125rem; color: #555; background: transparent; border: 1px solid var(--border-color); border-radius: 0.375rem; padding: 0.25rem 0.5rem; cursor: pointer; outline: none; &:focus { border-color: var(--brand-from); } } .thinking-toggle { display: flex; align-items: center; gap: 0.25rem; font-size: 0.8125rem; color: #888; cursor: pointer; user-select: none; input[type="checkbox"] { width: 0.875rem; height: 0.875rem; cursor: pointer; accent-color: var(--brand-from); } } }
.messages { flex: 1; overflow-y: auto; padding: 1.5rem; .welcome { text-align: center; padding: 3rem 0; color: #888; h2 { color: var(--text-color); margin-bottom: 0.5rem; } } }
</style>