<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

const props = defineProps<{
  isStreaming: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  send: [data: { text: string; file: File | null }]
  stop: []
}>()

const inputRef = ref<HTMLTextAreaElement>()
const fileInputRef = ref<HTMLInputElement>()
const userInput = ref('')
const selectedFile = ref<File | null>(null)

function triggerFileInput() { fileInputRef.value?.click() }

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    selectedFile.value = target.files[0]
  }
}

function removeFile() {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function sendMessage() {
  if (props.isStreaming) return
  if (!userInput.value.trim() && !selectedFile.value) return
  emit('send', { text: userInput.value.trim(), file: selectedFile.value })
  userInput.value = ''
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

watch(userInput, () => {
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
      inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 120) + 'px'
    }
  })
})

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<template>
  <div class="chat-input-area">
    <div v-if="selectedFile" class="file-preview">
      <span class="file-name">{{ selectedFile.name }}</span>
      <span class="file-size">({{ (selectedFile.size / 1024).toFixed(1) }} KB)</span>
      <button class="file-remove" @click="removeFile">✕</button>
    </div>
    <div class="input-row">
      <button class="upload-btn" @click="triggerFileInput" :disabled="isStreaming" title="上传文件">
        📎
      </button>
      <input type="file" ref="fileInputRef" @change="handleFileChange" style="display:none"
        accept=".pdf,.docx,.md,.txt,.jpg,.jpeg,.png,.gif,.webp,.mp3,.wav" />
      <textarea
        ref="inputRef"
        v-model="userInput"
        @keydown="onKeydown"
        :placeholder="placeholder || '输入消息...'"
        rows="1"
      ></textarea>
      <button
        v-if="isStreaming"
        class="stop-btn"
        @click="emit('stop')"
        title="停止生成"
      >
        ■
      </button>
      <button
        v-else
        class="send-btn"
        @click="sendMessage"
        :disabled="!userInput.trim() && !selectedFile"
      >
        ➤
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-input-area {
  flex-shrink: 0; padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color);
}
.file-preview {
  display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;
  padding: 0.5rem 0.75rem; background: rgba(0,124,240,0.05); border-radius: 0.375rem;
  font-size: 0.8125rem; color: #007CF0;
  .file-remove { background: none; border: none; color: #999; cursor: pointer; &:hover { color: #ff4d4f; } }
}
.input-row {
  display: flex; align-items: flex-end; gap: 0.5rem;
  padding: 0.625rem; background: var(--card-bg);
  border: 1px solid var(--border-color); border-radius: 0.75rem;
  textarea {
    flex: 1; resize: none; border: none; outline: none;
    background: transparent; color: var(--text-color);
    font: inherit; font-size: 0.9375rem; line-height: 1.5;
    max-height: 120px; padding: 0.375rem 0;
    &::placeholder { color: #999; }
  }
  .upload-btn {
    width: 36px; height: 36px; border: none; border-radius: 0.5rem;
    background: rgba(0,124,240,0.08); color: #007CF0; cursor: pointer; font-size: 1rem;
    display: flex; align-items: center; justify-content: center;
    &:hover:not(:disabled) { background: rgba(0,124,240,0.15); }
    &:disabled { opacity: 0.5; cursor: not-allowed; }
  }
  .send-btn {
    width: 36px; height: 36px; border: none; border-radius: 0.5rem;
    background: #007CF0; color: #fff; cursor: pointer; font-size: 1rem;
    display: flex; align-items: center; justify-content: center;
    transition: all 0.2s;
    &:hover:not(:disabled) { background: #0066cc; }
    &:disabled { background: #ccc; cursor: not-allowed; }
  }
  .stop-btn {
    width: 36px; height: 36px; border: none; border-radius: 0.5rem;
    background: #ff4d4f; color: #fff; cursor: pointer; font-size: 0.75rem;
    display: flex; align-items: center; justify-content: center;
    transition: all 0.2s;
    &:hover { background: #ff7875; }
  }
}
</style>