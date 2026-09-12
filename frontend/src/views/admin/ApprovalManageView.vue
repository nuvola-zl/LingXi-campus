<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPendingApprovals, approveLeave, approvePunchCorrection, approvePurchaseRequest, approvePurchase } from '@/api/approval'
import { ElMessage } from 'element-plus'

const approvals = ref<any[]>([])
const commentText = ref('')

async function load() {
  const res = await getPendingApprovals()
  approvals.value = res || []
}

async function handleApprove(type: string, id: number) {
  try {
    if (type === 'leave') await approveLeave(id, true, commentText.value)
    else if (type === 'punch') await approvePunchCorrection(id, true, commentText.value)
    else if (type === 'purchase') await approvePurchaseRequest(id, true, commentText.value)
    ElMessage.success('已批准')
    commentText.value = ''
    load()
  } catch { ElMessage.error('操作失败') }
}

async function handleReject(type: string, id: number) {
  try {
    if (type === 'leave') await approveLeave(id, false, commentText.value)
    else if (type === 'punch') await approvePunchCorrection(id, false, commentText.value)
    else if (type === 'purchase') await approvePurchaseRequest(id, false, commentText.value)
    ElMessage.success('已驳回')
    commentText.value = ''
    load()
  } catch { ElMessage.error('操作失败') }
}

async function handleArrived(orderNo: string) {
  try {
    await approvePurchase(orderNo)
    ElMessage.success('已确认到货')
    load()
  } catch { ElMessage.error('操作失败') }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>审批管理</h1>
    <div class="list">
      <div v-for="a in approvals" :key="a.id" class="approval-item">
        <div class="info">
          <span class="type-tag">{{ a.type === 'leave' ? '请假' : a.type === 'punch' ? '打卡补正' : '采购' }}</span>
          <span class="desc">{{ a.title || a.reason }}</span>
          <span class="requester">{{ a.requester }}</span>
        </div>
        <div class="actions">
          <input v-model="commentText" placeholder="审批意见（可选）" style="margin-right:0.5rem;padding:0.25rem 0.5rem;border:1px solid var(--border-color);border-radius:0.25rem;background:var(--bg-color);color:var(--text-color);width:140px" />
          <button v-if="a.type !== 'purchase' || a.status === 1" class="btn-approve" @click="handleApprove(a.type, a.id)">批准</button>
          <button v-if="a.type !== 'purchase' || a.status === 1" class="btn-reject" @click="handleReject(a.type, a.id)">驳回</button>
          <button v-if="a.type === 'purchase' && a.orderNo" class="btn-arrive" @click="handleArrived(a.orderNo)">确认到货</button>
        </div>
      </div>
      <el-empty v-if="approvals.length === 0" description="暂无待审批项" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; h1 { font-size: 1.25rem; margin-bottom: 1.5rem; } }
.approval-item { display: flex; justify-content: space-between; align-items: center; padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; margin-bottom: 0.5rem; .info { .type-tag { padding: 0.125rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; background: rgba(0,124,240,0.1); color: #007CF0; margin-right: 0.75rem; } .desc { font-size: 0.9375rem; } .requester { color: #888; font-size: 0.8125rem; margin-left: 1rem; } } }
.actions { display: flex; align-items: center; gap: 0.375rem; button { padding: 0.375rem 0.875rem; border: none; border-radius: 0.375rem; cursor: pointer; font-size: 0.8125rem; } .btn-approve { background: #10b981; color: #fff; } .btn-reject { background: #ff4d4f; color: #fff; } .btn-arrive { background: #007CF0; color: #fff; } }
</style>