<script setup lang="ts">
import { ref } from 'vue'
import { chatAPI } from '@/api/chat'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  sessions: any[]
  currentSessionId: number | null
}>()

const emit = defineEmits<{
  select: [id: number]
  newChat: []
  refresh: []
}>()

const editingId = ref<number | null>(null)
const editTitle = ref('')

function startRename(s: any) {
  editingId.value = s.id
  editTitle.value = s.title || ''
}

async function confirmRename() {
  if (!editingId.value) return
  try {
    await chatAPI.updateSession(editingId.value, editTitle.value || undefined)
    ElMessage.success('已重命名')
    emit('refresh')
  } catch {
    ElMessage.error('重命名失败')
  } finally {
    editingId.value = null
  }
}

function cancelRename() {
  editingId.value = null
}

async function handleDelete(s: any) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${s.title || '新对话'}」吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await chatAPI.deleteSession(s.id)
    ElMessage.success('已删除')
    emit('refresh')
  } catch {
    // cancelled
  }
}
</script>

<template>
  <aside class="chat-sidebar">
    <div class="sidebar-top">
      <button class="new-chat-btn" @click="emit('newChat')">+ 新对话</button>
    </div>
    <div class="session-list">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: s.id === currentSessionId }"
        @click="emit('select', s.id)"
      >
        <span class="session-icon">💬</span>

        <!-- 编辑模式 -->
        <input
          v-if="editingId === s.id"
          class="edit-input"
          v-model="editTitle"
          @keydown.enter="confirmRename"
          @keydown.escape="cancelRename"
          @blur="confirmRename"
          @click.stop
          ref="editInputRef"
        />

        <!-- 显示模式 -->
        <span v-else class="session-title">{{ s.title || '新对话' }}</span>

        <!-- 悬停操作按钮 -->
        <div class="session-actions" @click.stop>
          <button class="action-btn" title="重命名" @click="startRename(s)">
            ✎
          </button>
          <button class="action-btn action-delete" title="删除" @click="handleDelete(s)">
            ✕
          </button>
        </div>
      </div>
      <div v-if="sessions.length === 0" class="empty-hint">暂无对话记录</div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.chat-sidebar {
  width: 240px; flex-shrink: 0;
  background: var(--card-bg); border-right: 1px solid var(--border-color);
  display: flex; flex-direction: column;
}
.sidebar-top {
  padding: 1rem; border-bottom: 1px solid var(--border-color);
  .new-chat-btn {
    width: 100%; padding: 0.625rem; border: 1px dashed rgba(0,124,240,0.4);
    border-radius: 0.5rem; background: transparent; color: #007CF0;
    font-size: 0.875rem; cursor: pointer; transition: all 0.2s;
    &:hover { background: rgba(0,124,240,0.05); }
  }
}
.session-list {
  flex: 1; overflow-y: auto; padding: 0.5rem;
}
.session-item {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.625rem 0.75rem; border-radius: 0.5rem; cursor: pointer;
  font-size: 0.8125rem; color: var(--text-color); transition: all 0.2s;
  position: relative;
  &:hover { background: rgba(0,124,240,0.05); }
  &:hover .session-actions { opacity: 1; }
  &.active { background: rgba(0,124,240,0.1); color: #007CF0; }
}
.session-icon { font-size: 1rem; flex-shrink: 0; }
.session-title {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1;
}
.edit-input {
  flex: 1; padding: 0.125rem 0.25rem; border: 1px solid #007CF0;
  border-radius: 0.25rem; font-size: 0.8125rem; outline: none;
  background: var(--bg-color); color: var(--text-color);
}
.session-actions {
  display: flex; gap: 0.125rem; opacity: 0; transition: opacity 0.15s;
  flex-shrink: 0;
}
.action-btn {
  width: 22px; height: 22px; border: none; background: transparent;
  color: #999; font-size: 0.75rem; cursor: pointer;
  border-radius: 0.25rem; display: flex; align-items: center; justify-content: center;
  &:hover { background: rgba(0,0,0,0.08); color: #333; }
  &.action-delete:hover { color: #ff4d4f; background: rgba(255,77,79,0.1); }
}
.empty-hint { text-align: center; color: #999; padding: 2rem 0; font-size: 0.8125rem; }
</style>