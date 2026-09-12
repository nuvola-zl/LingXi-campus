<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserTicketList } from '@/api/ticket'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const tickets = ref<any[]>([])
const loading = ref(true)

async function loadTickets() {
  loading.value = true
  try {
    tickets.value = await getUserTicketList()
  } catch {
    ElMessage.error('加载报修单列表失败')
  } finally {
    loading.value = false
  }
}

const statusMap: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: '#f59e0b' },
  PROCESSING: { label: '处理中', color: '#3b82f6' },
  RESOLVED: { label: '已解决', color: '#10b981' },
  CLOSED: { label: '已关闭', color: '#6b7280' },
}

onMounted(loadTickets)
</script>

<template>
  <div class="page">
    <div class="page-header"><h1>我的报修单</h1></div>
    <div class="ticket-list" v-loading="loading">
      <div v-for="t in tickets" :key="t.id" class="ticket-item" @click="router.push(`/it/tickets/${t.id}`)">
        <div class="ticket-info">
          <span class="ticket-no">{{ t.ticketNo }}</span>
          <span class="ticket-title">{{ t.title }}</span>
        </div>
        <span class="ticket-status" :style="{ color: statusMap[t.status]?.color }">
          {{ statusMap[t.status]?.label || t.status }}
        </span>
      </div>
      <el-empty v-if="!loading && tickets.length === 0" description="暂无工单" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.page-header { margin-bottom: 1.5rem; h1 { font-size: 1.25rem; } }
.ticket-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color);
  border-radius: 0.5rem; margin-bottom: 0.5rem; cursor: pointer;
  &:hover { border-color: #007CF0; }
  .ticket-no { font-size: 0.75rem; color: #888; margin-right: 0.75rem; }
  .ticket-title { font-size: 0.9375rem; font-weight: 500; }
  .ticket-status { font-size: 0.8125rem; font-weight: 500; }
}
</style>