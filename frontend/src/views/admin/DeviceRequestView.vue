<script setup lang="ts">
import { ref, watch } from 'vue'
import { applyDevice, checkDeviceStatus, confirmReceive, cancelRequest, returnDevice, getMyRequests } from '@/api/device'
import { ElMessage } from 'element-plus'

const activeTab = ref<'apply' | 'my' | 'query'>('apply')

// ---- 申请器材 ----
const applyForm = ref({ deviceType: '', reason: '' })
const applying = ref(false)

async function handleApply() {
  if (!applyForm.value.deviceType || !applyForm.value.reason) {
    ElMessage.warning('请填写完整信息')
    return
  }
  applying.value = true
  try {
    const res = await applyDevice(applyForm.value)
    ElMessage.success(`提交成功，借用编号: ${res?.requestNo || ''}`)
    applyForm.value = { deviceType: '', reason: '' }
  } catch { ElMessage.error('提交失败') }
  finally { applying.value = false }
}

// ---- 我的借用 ----
const myRequests = ref<any[]>([])
const myLoading = ref(false)
const returnReason = ref('')
const returningRequestNo = ref<string | null>(null)

const statusLabel = (s: number) => ({ 2: '购置中', 3: '准备中', 4: '待领取', 5: '已完成', 6: '已取消', 7: '归还中' }[s] || `未知(${s})`)
const statusColor = (s: number) => ({ 2: '#f59e0b', 3: '#3b82f6', 4: '#10b981', 5: '#6b7280', 6: '#ef4444', 7: '#f59e0b' }[s] || '#6b7280')

async function loadMyRequests() {
  myLoading.value = true
  try {
    const res = await getMyRequests()
    myRequests.value = res || []
  } catch { ElMessage.error('加载失败') }
  finally { myLoading.value = false }
}

// 切换到"我的借用"tab时自动加载
watch(activeTab, (tab) => {
  if (tab === 'my') loadMyRequests()
})

async function handleConfirmReceive(requestNo: string) {
  try {
    await confirmReceive(requestNo)
    ElMessage.success('已确认领取')
    loadMyRequests()
  } catch { ElMessage.error('操作失败') }
}

async function handleCancel(requestNo: string) {
  try {
    await cancelRequest(requestNo)
    ElMessage.success('已取消申请')
    loadMyRequests()
  } catch { ElMessage.error('操作失败') }
}

async function handleReturn(requestNo: string) {
  if (!returnReason.value.trim()) {
    ElMessage.warning('请填写归还原因')
    return
  }
  try {
    await returnDevice(requestNo, returnReason.value)
    ElMessage.success('归还申请已提交，等待管理员确认')
    returnReason.value = ''
    returningRequestNo.value = null
    loadMyRequests()
  } catch { ElMessage.error('操作失败') }
}

// ---- 进度查询 ----
const queryRequestNo = ref('')
const queryResult = ref<any>(null)

async function handleQuery() {
  if (!queryRequestNo.value.trim()) return
  try {
    const res = await checkDeviceStatus(queryRequestNo.value.trim())
    queryResult.value = res
  } catch { ElMessage.error('查询失败') }
}

// ---- 器材类型（value 必须与库存表 device_type 一致）----
const deviceTypes = [
  { value: 'camera', label: '相机' },
  { value: 'projector', label: '投影仪' },
  { value: 'sports_kit', label: '运动器材' },
  { value: 'tent', label: '帐篷' },
  { value: 'other', label: '其他' },
]
</script>

<template>
  <div class="page">
    <h1>器材借用</h1>
    <div class="tabs">
      <button :class="{ active: activeTab === 'apply' }" @click="activeTab = 'apply'">借用器材</button>
      <button :class="{ active: activeTab === 'my' }" @click="activeTab = 'my'">我的借用</button>
      <button :class="{ active: activeTab === 'query' }" @click="activeTab = 'query'">进度查询</button>
    </div>

    <!-- ====== 借用器材 ====== -->
    <div v-if="activeTab === 'apply'" class="form-card">
      <div class="field">
        <label>器材类型</label>
        <select v-model="applyForm.deviceType">
          <option value="" disabled>请选择器材类型</option>
          <option v-for="dt in deviceTypes" :key="dt.value" :value="dt.value">{{ dt.label }}</option>
        </select>
      </div>
      <div class="field">
        <label>借用原因</label>
        <textarea v-model="applyForm.reason" rows="3" placeholder="请简要说明借用原因（如：社团活动拍摄）"></textarea>
      </div>
      <button class="btn-primary" :disabled="applying" @click="handleApply">
        {{ applying ? '提交中...' : '提交借用' }}
      </button>
    </div>

    <!-- ====== 我的借用 ====== -->
    <div v-if="activeTab === 'my'" class="my-device" v-loading="myLoading">
      <div v-if="myRequests.length === 0 && !myLoading" style="text-align:center;padding:2rem;color:#888;">
        暂无借用记录
      </div>

      <div v-for="req in myRequests" :key="req.requestNo" class="request-card">
        <div class="request-header">
          <span class="request-no">{{ req.requestNo }}</span>
          <span class="status-badge" :style="{ background: statusColor(req.status) }">
            {{ statusLabel(req.status) }}
          </span>
        </div>
        <div class="request-body">
          <div class="info-row"><span class="label">器材类型</span><span>{{ req.deviceType || '-' }}</span></div>
          <div class="info-row"><span class="label">借用原因</span><span>{{ req.reason || '-' }}</span></div>
          <div v-if="req.allocatedDeviceId" class="info-row">
            <span class="label">分配器材ID</span><span>{{ req.allocatedDeviceId }}</span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="req.status === 4 || req.status === 2 || req.status === 5" class="request-actions">
          <button v-if="req.status === 4" class="btn-action btn-confirm" @click="handleConfirmReceive(req.requestNo)">
            确认已领取
          </button>
          <button v-if="req.status === 2 || req.status === 4" class="btn-action btn-cancel" @click="handleCancel(req.requestNo)">
            取消申请
          </button>
          <button v-if="req.status === 5 && req.allocatedDeviceId && returningRequestNo !== req.requestNo" class="btn-action btn-return" @click="returningRequestNo = req.requestNo">
            归还器材
          </button>
        </div>

        <!-- 归还原因输入 -->
        <div v-if="returningRequestNo === req.requestNo" class="return-form">
          <textarea v-model="returnReason" rows="2" placeholder="请输入归还原因"></textarea>
          <div class="return-btns">
            <button class="btn-primary" @click="handleReturn(req.requestNo)">确认归还</button>
            <button class="btn-cancel" @click="returningRequestNo = null; returnReason = ''">取消</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 进度查询 ====== -->
    <div v-if="activeTab === 'query'" class="form-card">
      <div class="field row">
        <label>借用编号</label>
        <input v-model="queryRequestNo" placeholder="输入借用编号" />
        <button class="btn-primary" @click="handleQuery">查询</button>
      </div>
      <div v-if="queryResult" class="result-card">
        <div class="result-header">
          <span>{{ queryResult.requestNo }}</span>
          <span class="status-badge" :style="{ background: statusColor(queryResult.status) }">
            {{ statusLabel(queryResult.status) }}
          </span>
        </div>
        <div class="result-body">
          <div class="info-row"><span class="label">器材类型</span><span>{{ queryResult.deviceType || '-' }}</span></div>
          <div class="info-row"><span class="label">借用原因</span><span>{{ queryResult.reason || '-' }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; h1 { font-size: 1.25rem; margin-bottom: 1rem; } }

.tabs { display: flex; gap: 0.5rem; margin-bottom: 1.5rem;
  button { padding: 0.5rem 1rem; border: 1px solid var(--border-color); background: var(--card-bg); color: var(--text-color); border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; transition: all 0.2s;
    &.active { background: var(--brand-from); color: #fff; border-color: var(--brand-from); }
  }
}

// ---- 表单 ----
.form-card { max-width: 500px; padding: 1.5rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.75rem; }
.field { margin-bottom: 1rem;
  label { display: block; margin-bottom: 0.375rem; font-size: 0.875rem; color: var(--text-color); }
  input, select, textarea { width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); font-size: 0.875rem; }
  select { cursor: pointer; }
  &.row { display: flex; align-items: center; gap: 0.5rem; input { flex: 1; } }
}

// ---- 按钮 ----
.btn-primary { padding: 0.5rem 1.25rem; background: var(--brand-from); color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; &:disabled { opacity: 0.5; cursor: not-allowed; } }
.btn-cancel { padding: 0.5rem 1.25rem; background: transparent; color: var(--text-color); border: 1px solid var(--border-color); border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; }

// ---- 我的借用 ----
.my-device { max-width: 600px; }

// ---- 状态标签 ----
.status-badge { padding: 0.25rem 0.75rem; border-radius: 0.25rem; color: #fff; font-size: 0.75rem; font-weight: 500; white-space: nowrap; }

// ---- 卡片 ----
.request-card, .result-card { padding: 1.25rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; }
.request-header, .result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--border-color);
  .request-no { font-weight: 500; font-size: 0.9375rem; }
}
.request-body, .result-body { display: flex; flex-direction: column; gap: 0.5rem; }
.info-row { display: flex; gap: 1rem; font-size: 0.875rem;
  .label { color: #888; min-width: 80px; }
}

.request-actions { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color); display: flex; gap: 0.5rem; flex-wrap: wrap; }
.btn-action { padding: 0.5rem 1rem; border: none; border-radius: 0.375rem; cursor: pointer; font-size: 0.8125rem; color: #fff;
  &.btn-confirm { background: #10b981; }
  &.btn-cancel { background: #ff4d4f; }
  &.btn-return { background: #f59e0b; }
}

.return-form { margin-top: 1rem; textarea { width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); resize: vertical; } .return-btns { display: flex; gap: 0.5rem; margin-top: 0.5rem; } }
</style>