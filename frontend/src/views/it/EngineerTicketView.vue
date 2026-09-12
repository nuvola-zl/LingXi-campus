<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getEngineerPending, getEngineerHistory, claimTicket, addTicketComment, resolveTicket, getEngineerTicketDetail } from '@/api/ticket'
import { ElMessage } from 'element-plus'

const pendingTickets = ref<any[]>([])
const historyTickets = ref<any[]>([])
const activeTab = ref('pending')
const selectedTicket = ref<any>(null)
const commentText = ref('')


async function loadPending() {
  const res = await getEngineerPending()
  pendingTickets.value = res.data || []
}

async function loadHistory() {
  const res = await getEngineerHistory()
  historyTickets.value = res.data || []
}

async function viewDetail(ticketId: number) {
  const res = await getEngineerTicketDetail(ticketId)
  selectedTicket.value = res.data || res
}

async function handleClaim(ticketId: number) {
  try {
    await claimTicket(ticketId)
    ElMessage.success('已认领')
    loadPending()
  } catch { ElMessage.error('认领失败') }
}

async function handleComment() {
  if (!selectedTicket.value || !commentText.value.trim()) return
  try {
    await addTicketComment(selectedTicket.value.id, commentText.value)
    ElMessage.success('评论已添加')
    commentText.value = ''
    viewDetail(selectedTicket.value.id)
  } catch { ElMessage.error('添加评论失败') }
}

async function handleResolve() {
  if (!selectedTicket.value) return
  try {
    await resolveTicket(selectedTicket.value.id)
    ElMessage.success('报修单已解决')
    selectedTicket.value = null
    loadPending()
  } catch { ElMessage.error('操作失败') }
}

onMounted(() => { loadPending(); loadHistory() })
</script>

<template>
  <div class="page">
    <div class="tabs">
      <button :class="{ active: activeTab === 'pending' }" @click="activeTab = 'pending'">待处理 ({{ pendingTickets.length }})</button>
      <button :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">已处理</button>
    </div>

    <div v-if="activeTab === 'pending' && !selectedTicket" class="list">
      <div v-for="t in pendingTickets" :key="t.id" class="item">
        <div class="info">
          <span class="no">{{ t.ticketNo }}</span>
          <span class="title">{{ t.title }}</span>
          <span class="time">{{ t.createdAt }}</span>
        </div>
        <div class="actions">
          <button @click="viewDetail(t.id)">查看</button>
          <button class="btn-claim" @click="handleClaim(t.id)">认领</button>
        </div>
      </div>
      <el-empty v-if="pendingTickets.length === 0" description="暂无待处理报修单" />
    </div>

    <div v-if="activeTab === 'history' && !selectedTicket" class="list">
      <div v-for="t in historyTickets" :key="t.id" class="item">
        <span class="no">{{ t.ticketNo }}</span>
        <span class="title">{{ t.title }}</span>
        <button @click="viewDetail(t.id)">查看</button>
      </div>
      <el-empty v-if="historyTickets.length === 0" description="暂无历史报修单" />
    </div>

    <div v-if="selectedTicket" class="detail-panel">
      <button class="back-btn" @click="selectedTicket = null">← 返回</button>
      <h2>{{ selectedTicket.title }}</h2>
      <div class="meta">报修单号: {{ selectedTicket.ticketNo }} | {{ selectedTicket.status }}</div>
      <p class="content">{{ selectedTicket.description }}</p>

      <div class="comments">
        <h4>处理记录</h4>
        <div v-for="c in selectedTicket.comments" :key="c.id" class="comment">
          <span class="author">{{ c.userName }}</span>: {{ c.content }}
        </div>
      </div>

      <div class="action-form">
        <textarea v-model="commentText" placeholder="添加评论..." rows="2"></textarea>
        <button class="btn-primary" @click="handleComment">提交评论</button>
      </div>

      <div class="action-form">
        <button class="btn-resolve" @click="handleResolve">标记已解决</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem;
  button { padding: 0.5rem 1rem; border: 1px solid var(--border-color); background: var(--card-bg); color: var(--text-color); border-radius: 0.375rem; cursor: pointer; &.active { background: #007CF0; color: #fff; border-color: #007CF0; } }
}
.list { .item { display: flex; justify-content: space-between; align-items: center; padding: 0.75rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.375rem; margin-bottom: 0.375rem; font-size: 0.875rem; .info { .no { color: #888; margin-right: 0.75rem; } .time { margin-left: 1rem; color: #aaa; font-size: 0.75rem; } } button { padding: 0.25rem 0.75rem; border: 1px solid #007CF0; background: transparent; color: #007CF0; border-radius: 0.25rem; cursor: pointer; margin-left: 0.375rem; &.btn-claim { background: #007CF0; color: #fff; } } } }
.back-btn { background: none; border: none; color: #007CF0; cursor: pointer; font-size: 0.875rem; margin-bottom: 1rem; }
.detail-panel { h2 { margin-bottom: 0.5rem; } .meta { color: #888; font-size: 0.8125rem; margin-bottom: 1rem; } .content { white-space: pre-wrap; line-height: 1.6; margin-bottom: 1rem; } }
.comments { margin-bottom: 1rem; h4 { margin-bottom: 0.5rem; } .comment { font-size: 0.875rem; padding: 0.375rem 0; border-bottom: 1px solid var(--border-color); .author { font-weight: 500; } } }
.action-form { margin-bottom: 0.75rem; textarea { width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); resize: vertical; } }
.btn-primary { padding: 0.5rem 1rem; background: #007CF0; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; margin-top: 0.25rem; }
.btn-resolve { padding: 0.5rem 1rem; background: #10b981; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; margin-top: 0.25rem; }
</style>