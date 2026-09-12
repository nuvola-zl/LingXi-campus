<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserTicketDetail } from '@/api/ticket'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const ticketId = Number(route.params.id)
const ticket = ref<any>(null)
const comments = ref<any[]>([])
const loading = ref(true)

const statusMap: Record<number, { label: string; color: string }> = {
  1: { label: '待处理', color: '#f59e0b' },
  2: { label: '处理中', color: '#3b82f6' },
  3: { label: '待确认', color: '#8b5cf6' },
  4: { label: '已关闭', color: '#6b7280' },
}

const priorityMap: Record<number, { label: string; color: string }> = {
  1: { label: '紧急', color: '#ff4d4f' },
  2: { label: '普通', color: '#3b82f6' },
  3: { label: '低', color: '#10b981' },
}

async function loadDetail() {
  loading.value = true
  try {
    const data = await getUserTicketDetail(ticketId)
    if (!data) { ElMessage.error('报修单不存在'); router.replace('/it/tickets'); return }
    ticket.value = data.ticket
    comments.value = data.comments || []
  } catch {
    ElMessage.error('加载报修单详情失败')
  } finally {
    loading.value = false
  }
}

function formatDate(d: string) {
  if (!d) return ''
  return d.replace('T', ' ').substring(0, 19)
}

onMounted(loadDetail)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <router-link to="/it/tickets">← 返回列表</router-link>
    </div>

    <div v-if="!loading && ticket" class="ticket-detail">
      <div class="detail-header">
        <div class="title-row">
          <span class="priority-tag" :style="{ color: priorityMap[ticket.priority]?.color, borderColor: priorityMap[ticket.priority]?.color }">
            {{ priorityMap[ticket.priority]?.label || '普通' }}
          </span>
          <h1>{{ ticket.title }}</h1>
        </div>
        <span class="status-tag" :style="{ background: statusMap[ticket.status]?.color }">
          {{ statusMap[ticket.status]?.label || ticket.status }}
        </span>
      </div>

      <div class="detail-meta">
        <span>工单号: {{ ticket.ticketNo }}</span>
        <span>创建时间: {{ formatDate(ticket.createdAt) }}</span>
        <span v-if="ticket.resolvedAt">解决时间: {{ formatDate(ticket.resolvedAt) }}</span>
        <span v-if="ticket.closedAt">关闭时间: {{ formatDate(ticket.closedAt) }}</span>
      </div>

      <div class="detail-body">
        <h3>问题描述</h3>
        <p class="description">{{ ticket.description || '暂无描述' }}</p>
      </div>

      <div class="timeline">
        <h3>处理记录 ({{ comments.length }})</h3>
        <div v-if="comments.length === 0" class="empty">暂无处理记录</div>
        <div v-for="c in comments" :key="c.id" class="timeline-item">
          <div class="timeline-dot" :class="c.type === 'SYSTEM' ? 'system' : 'engineer'"></div>
          <div class="timeline-content">
            <div class="timeline-header">
              <span class="actor">{{ c.type === 'SYSTEM' ? '系统' : '工程师' }}</span>
              <span class="time">{{ formatDate(c.createdAt) }}</span>
            </div>
            <p>{{ c.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading" v-loading="true"></div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.page-header { margin-bottom: 1.5rem; a { color: #007CF0; text-decoration: none; font-size: 0.875rem; } }
.loading { min-height: 200px; }

.ticket-detail {
  max-width: 800px;
}

.detail-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;
  .title-row { display: flex; align-items: center; gap: 0.5rem;
    h1 { font-size: 1.25rem; font-weight: 600; }
  }
  .priority-tag { font-size: 0.75rem; padding: 1px 6px; border: 1px solid; border-radius: 3px; }
  .status-tag { font-size: 0.75rem; padding: 2px 10px; border-radius: 0.75rem; color: #fff; white-space: nowrap; }
}

.detail-meta {
  display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.5rem;
  padding: 0.75rem 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem;
  font-size: 0.8125rem; color: #888;
}

.detail-body {
  margin-bottom: 2rem;
  h3 { font-size: 0.9375rem; margin-bottom: 0.5rem; }
  .description { white-space: pre-wrap; line-height: 1.7; color: var(--text-color); font-size: 0.9375rem; }
}

.timeline {
  h3 { font-size: 0.9375rem; margin-bottom: 1rem; }
  .empty { color: #888; font-size: 0.875rem; padding: 1rem 0; }
}

.timeline-item {
  display: flex; gap: 0.75rem; margin-bottom: 1rem; padding-left: 0.375rem;
  .timeline-dot {
    width: 10px; height: 10px; border-radius: 50%; margin-top: 0.35rem; flex-shrink: 0;
    background: #d1d5db;
    &.system { background: #f59e0b; }
    &.engineer { background: #3b82f6; }
  }
  .timeline-content {
    flex: 1;
    .timeline-header { display: flex; gap: 0.75rem; align-items: baseline; margin-bottom: 0.25rem;
      .actor { font-weight: 500; font-size: 0.875rem; }
      .time { font-size: 0.75rem; color: #aaa; }
    }
    p { font-size: 0.875rem; line-height: 1.5; color: var(--text-color); }
  }
}
</style>