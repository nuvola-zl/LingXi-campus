<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { applyPunchCorrection, getPunchList } from '@/api/punch'
import { ElMessage } from 'element-plus'

const punches = ref<any[]>([])
const showForm = ref(false)
const form = ref({ punchDate: '', punchType: 'miss_punch', reason: '' })

async function load() {
  const res = await getPunchList()
  punches.value = res || []
}

async function handleSubmit() {
  if (!form.value.punchDate || !form.value.reason) { ElMessage.warning('请填写完整信息'); return }
  try {
    await applyPunchCorrection(form.value)
    ElMessage.success('提交成功')
    showForm.value = false
    form.value = { punchDate: '', punchType: 'miss_punch', reason: '' }
    load()
  } catch { ElMessage.error('提交失败') }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>打卡补正</h1>
      <button class="btn-primary" @click="showForm = !showForm">{{ showForm ? '取消' : '+ 新申请' }}</button>
    </div>
    <div v-if="showForm" class="form-card">
      <input v-model="form.punchDate" type="date" />
      <select v-model="form.punchType"><option value="miss_punch">忘打卡</option><option value="late">迟到</option></select>
      <textarea v-model="form.reason" placeholder="补正原因" rows="2"></textarea>
      <button class="btn-primary" @click="handleSubmit">提交申请</button>
    </div>
    <div class="list">
      <div v-for="p in punches" :key="p.id" class="item">
        <span>{{ p.punchDate }} {{ p.punchType === 'miss_punch' ? '忘打卡' : '迟到' }}</span>
        <span class="status">{{ p.status }}</span>
      </div>
      <el-empty v-if="punches.length === 0" description="暂无打卡记录" />
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
.item { display: flex; gap: 1rem; padding: 0.75rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.375rem; margin-bottom: 0.375rem; font-size: 0.875rem; .status { color: #888; } }
</style>