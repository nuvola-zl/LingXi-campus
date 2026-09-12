<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getDeviceInventory, getPurchaseList, confirmHandover, confirmDeviceReturn, approvePurchaseRequest, approvePurchase, getPendingReturns } from '@/api/approval'
import { ElMessage } from 'element-plus'

const activeTab = ref<'inventory' | 'purchase' | 'handover'>('inventory')

// ---- 器材库存 ----
const inventory = ref<any[]>([])
const inventoryLoading = ref(false)

async function loadInventory() {
  inventoryLoading.value = true
  try {
    const res = await getDeviceInventory()
    inventory.value = res || []
  } catch { ElMessage.error('加载库存失败') }
  finally { inventoryLoading.value = false }
}

// ---- 采购单管理 ----
const purchaseList = ref<any[]>([])
const purchaseLoading = ref(false)
const purchaseFilter = ref<{ status?: number; page: number; size: number }>({ page: 1, size: 10 })
const purchaseTotal = ref(0)

const purchaseStatusLabel = (s: number) => ({ 1: '待审批', 2: '已批准', 3: '采购中', 4: '已到货', 5: '已取消' }[s] || `未知(${s})`)
const purchaseStatusColor = (s: number) => ({ 1: '#f59e0b', 2: '#3b82f6', 3: '#8b5cf6', 4: '#10b981', 5: '#ef4444' }[s] || '#6b7280')

async function loadPurchases() {
  purchaseLoading.value = true
  try {
    const res = await getPurchaseList(purchaseFilter.value)
    if (res) {
      purchaseList.value = res.records || []
      purchaseTotal.value = res.total || 0
    }
  } catch { ElMessage.error('加载采购单失败') }
  finally { purchaseLoading.value = false }
}

function onPurchasePageChange(page: number) {
  purchaseFilter.value.page = page
  loadPurchases()
}

// ---- 采购单操作 ----
const purchaseActionLoading = ref<string | null>(null)

async function handleApprovePurchase(po: any) {
  purchaseActionLoading.value = po.orderNo
  try {
    await approvePurchaseRequest(po.id, true, '')
    ElMessage.success(`采购单 ${po.orderNo} 已批准`)
    loadPurchases()
  } catch { ElMessage.error('操作失败') }
  finally { purchaseActionLoading.value = null }
}

async function handleRejectPurchase(po: any) {
  purchaseActionLoading.value = po.orderNo
  try {
    await approvePurchaseRequest(po.id, false, '')
    ElMessage.success(`采购单 ${po.orderNo} 已驳回`)
    loadPurchases()
  } catch { ElMessage.error('操作失败') }
  finally { purchaseActionLoading.value = null }
}

async function handleConfirmArrived(po: any) {
  purchaseActionLoading.value = po.orderNo
  try {
    await approvePurchase(po.orderNo)
    ElMessage.success(`采购单 ${po.orderNo} 已确认到货，系统将自动分配器材`)
    loadPurchases()
  } catch { ElMessage.error('操作失败') }
  finally { purchaseActionLoading.value = null }
}

// ---- 器材交接 ----
const handoverRequestNo = ref('')
const handoverLoading = ref(false)

async function handleConfirmHandover() {
  if (!handoverRequestNo.value.trim()) return
  handoverLoading.value = true
  try {
    await confirmHandover(handoverRequestNo.value.trim())
    ElMessage.success('器材交接确认成功')
    handoverRequestNo.value = ''
  } catch { ElMessage.error('操作失败') }
  finally { handoverLoading.value = false }
}

const pendingReturns = ref<any[]>([])
const returnLoading = ref(false)
const returnActionLoading = ref<number | null>(null)

async function loadPendingReturns() {
  returnLoading.value = true
  try {
    const res = await getPendingReturns()
    pendingReturns.value = res || []
  } catch { ElMessage.error('加载归还记录失败') }
  finally { returnLoading.value = false }
}

async function handleConfirmReturn(returnId: number) {
  returnActionLoading.value = returnId
  try {
    await confirmDeviceReturn(returnId)
    ElMessage.success('归还确认成功，器材已入库')
    loadPendingReturns()
  } catch { ElMessage.error('操作失败') }
  finally { returnActionLoading.value = null }
}

onMounted(() => {
  loadInventory()
  loadPurchases()
})

watch(activeTab, (tab) => {
  if (tab === 'handover') loadPendingReturns()
})
</script>

<template>
  <div class="page">
    <h1>器材管理</h1>
    <div class="tabs">
      <button :class="{ active: activeTab === 'inventory' }" @click="activeTab = 'inventory'">器材库存</button>
      <button :class="{ active: activeTab === 'purchase' }" @click="activeTab = 'purchase'">采购单管理</button>
      <button :class="{ active: activeTab === 'handover' }" @click="activeTab = 'handover'">器材交接</button>
    </div>

    <!-- ====== 器材库存 ====== -->
    <div v-if="activeTab === 'inventory'" v-loading="inventoryLoading">
      <table v-if="inventory.length" class="data-table">
        <thead>
        <tr>
          <th>ID</th>
          <th>器材类型</th>
          <th>型号</th>
          <th>总量</th>
          <th>可用</th>
          <th>存放位置</th>
          <th>更新时间</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in inventory" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.deviceType }}</td>
          <td>{{ item.model || '-' }}</td>
          <td>{{ item.totalCount }}</td>
          <td>
            <span :class="{ 'low-stock': item.availableCount === 0 }">{{ item.availableCount }}</span>
          </td>
          <td>{{ item.location || '-' }}</td>
          <td>{{ item.updatedAt || '-' }}</td>
        </tr>
        </tbody>
      </table>
      <el-empty v-else description="暂无库存数据" />
    </div>

    <!-- ====== 采购单管理 ====== -->
    <div v-if="activeTab === 'purchase'" v-loading="purchaseLoading">
      <div class="filter-bar">
        <select v-model.number="purchaseFilter.status" @change="purchaseFilter.page = 1; loadPurchases()">
          <option :value="undefined">全部状态</option>
          <option :value="1">待审批</option>
          <option :value="2">已批准</option>
          <option :value="3">采购中</option>
          <option :value="4">已到货</option>
          <option :value="5">已取消</option>
        </select>
      </div>
      <table v-if="purchaseList.length" class="data-table">
        <thead>
        <tr>
          <th>采购单号</th>
          <th>器材类型</th>
          <th>数量</th>
          <th>原因</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>到货时间</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="po in purchaseList" :key="po.id">
          <td class="mono">{{ po.orderNo }}</td>
          <td>{{ po.deviceType }}</td>
          <td>{{ po.quantity }}</td>
          <td class="ellipsis">{{ po.reason || '-' }}</td>
          <td>
              <span class="status-badge" :style="{ background: purchaseStatusColor(po.status) }">
                {{ purchaseStatusLabel(po.status) }}
              </span>
          </td>
          <td>{{ po.createdAt || '-' }}</td>
          <td>{{ po.arrivedAt || '-' }}</td>
          <td class="actions-cell">
            <template v-if="po.status === 1">
              <button class="btn-approve" :disabled="purchaseActionLoading === po.orderNo" @click="handleApprovePurchase(po)">批准</button>
              <button class="btn-reject" :disabled="purchaseActionLoading === po.orderNo" @click="handleRejectPurchase(po)">驳回</button>
            </template>
            <button v-if="po.status === 2" class="btn-arrive" :disabled="purchaseActionLoading === po.orderNo" @click="handleConfirmArrived(po)">确认到货</button>
            <span v-if="po.status !== 1 && po.status !== 2" class="no-action">-</span>
          </td>
        </tr>
        </tbody>
      </table>
      <el-empty v-else description="暂无采购单" />
      <div v-if="purchaseTotal > purchaseFilter.size" class="pagination">
        <el-pagination
            layout="prev, pager, next"
            :total="purchaseTotal"
            :page-size="purchaseFilter.size"
            :current-page="purchaseFilter.page"
            @current-change="onPurchasePageChange"
        />
      </div>
    </div>

    <!-- ====== 器材交接 ====== -->
    <div v-if="activeTab === 'handover'" class="handover-section">
      <div class="handover-card">
        <h3>确认器材交接</h3>
        <p class="hint">当学生前来领取器材时，输入借用单号确认交接</p>
        <div class="field row">
          <input v-model="handoverRequestNo" placeholder="输入借用单号 (如 DEV-xxx)" @keyup.enter="handleConfirmHandover" />
          <button class="btn-primary" :disabled="handoverLoading" @click="handleConfirmHandover">
            {{ handoverLoading ? '处理中...' : '确认交接' }}
          </button>
        </div>
      </div>

      <div class="handover-card" style="margin-top:1rem">
        <h3>待确认的归还申请</h3>
        <p class="hint">学生提交归还后，在此确认入库</p>
        <table v-if="pendingReturns.length" class="data-table">
          <thead>
          <tr>
            <th>ID</th>
            <th>借用单号</th>
            <th>用户ID</th>
            <th>器材ID</th>
            <th>归还原因</th>
            <th>申请时间</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="r in pendingReturns" :key="r.id">
            <td>{{ r.id }}</td>
            <td class="mono">{{ r.requestNo }}</td>
            <td>{{ r.userId }}</td>
            <td>{{ r.deviceId }}</td>
            <td class="ellipsis">{{ r.returnReason || '-' }}</td>
            <td>{{ r.createdAt || '-' }}</td>
            <td>
              <button class="btn-arrive" :disabled="returnActionLoading === r.id" @click="handleConfirmReturn(r.id)">
                {{ returnActionLoading === r.id ? '处理中...' : '确认入库' }}
              </button>
            </td>
          </tr>
          </tbody>
        </table>
        <el-empty v-else description="暂无待确认的归还申请" />
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

// ---- 表格 ----
.data-table { width: 100%; border-collapse: collapse; font-size: 0.875rem;
  th { text-align: left; padding: 0.75rem; background: var(--card-bg); border-bottom: 2px solid var(--border-color); font-weight: 500; color: #888; white-space: nowrap; }
  td { padding: 0.75rem; border-bottom: 1px solid var(--border-color); color: var(--text-color); }
  tr:hover td { background: rgba(99,102,241,0.03); }
  .mono { font-family: monospace; font-size: 0.8125rem; }
  .ellipsis { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .low-stock { color: #ef4444; font-weight: 500; }
}

.status-badge { padding: 0.2rem 0.625rem; border-radius: 0.25rem; color: #fff; font-size: 0.75rem; font-weight: 500; white-space: nowrap; }

.filter-bar { margin-bottom: 1rem;
  select { padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); font-size: 0.875rem; cursor: pointer; }
}

.pagination { margin-top: 1rem; display: flex; justify-content: center; }

// ---- 交接 ----
.handover-section { max-width: 900px; }
.handover-card { padding: 1.5rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.75rem;
  h3 { font-size: 1rem; margin-bottom: 0.375rem; }
  .hint { font-size: 0.8125rem; color: #888; margin-bottom: 1rem; }
}
.field { margin-bottom: 1rem;
  label { display: block; margin-bottom: 0.375rem; font-size: 0.875rem; color: var(--text-color); }
  input { width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); font-size: 0.875rem; }
  &.row { display: flex; align-items: center; gap: 0.5rem; input { flex: 1; } }
}
.btn-primary { padding: 0.5rem 1.25rem; background: var(--brand-from); color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; &:disabled { opacity: 0.5; cursor: not-allowed; } }

.actions-cell { display: flex; gap: 0.375rem; flex-wrap: nowrap;
  button { padding: 0.3rem 0.625rem; border: none; border-radius: 0.25rem; cursor: pointer; font-size: 0.75rem; color: #fff; white-space: nowrap; &:disabled { opacity: 0.5; cursor: not-allowed; } }
  .btn-approve { background: #10b981; }
  .btn-reject { background: #ff4d4f; }
  .btn-arrive { background: var(--brand-from); }
  .no-action { color: #ccc; }
}
</style>