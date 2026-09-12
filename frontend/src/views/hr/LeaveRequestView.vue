<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { submitLeave, getLeaveList } from '@/api/leave'
import { ElMessage } from 'element-plus'

const leaves = ref<any[]>([])
const showForm = ref(false)
const form = ref({ type: 'ANNUAL', startDate: '', endDate: '', reason: '' })

async function load() {
  const res = await getLeaveList()
  leaves.value = res || []
}

async function handleSubmit() {
  if (!form.value.startDate || !form.value.endDate || !form.value.reason) {
    ElMessage.warning('请填写完整信息')
    return
  }
  const start = new Date(form.value.startDate)
  const end = new Date(form.value.endDate)
  const days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1
  try {
    await submitLeave({ ...form.value, days })
    ElMessage.success('提交成功')
    showForm.value = false
    form.value = { type: 'ANNUAL', startDate: '', endDate: '', reason: '' }
    load()
  } catch { ElMessage.error('提交失败') }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>请假申请</h1>
      <button class="btn-primary" @click="showForm = !showForm">{{ showForm ? '取消' : '+ 新申请' }}</button>
    </div>
    <div v-if="showForm" class="form-card">
      <select v-model="form.type">
        <option value="ANNUAL">年假</option>
        <option value="SICK">病假</option>
        <option value="PERSONAL">事假</option>
      </select>
      <input v-model="form.startDate" type="date" />
      <input v-model="form.endDate" type="date" />
      <textarea v-model="form.reason" placeholder="请假原因" rows="2"></textarea>
      <button class="btn-primary" @click="handleSubmit">提交申请</button>
    </div>
    <div class="list">
      <div v-for="l in leaves" :key="l.id" class="item">
        <span class="type">{{ l.type }}</span>
        <span>{{ l.startDate }} ~ {{ l.endDate }}</span>
        <span class="status">{{ l.status }}</span>
      </div>
      <el-empty v-if="leaves.length === 0" description="暂无请假记录" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; h1 { font-size: 1.25rem; } }
.btn-primary { padding: 0.5rem 1rem; background: #007CF0; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; }
.form-card { padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.5rem; max-width: 400px;
  select, input, textarea { padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); }
}
.item { display: flex; gap: 1rem; padding: 0.75rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.375rem; margin-bottom: 0.375rem; font-size: 0.875rem; .type { font-weight: 500; } .status { color: #888; } }
</style>