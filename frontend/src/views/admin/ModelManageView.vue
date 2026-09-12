<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getModels, addModel, deleteModel } from '@/api/model'
import { ElMessage, ElMessageBox } from 'element-plus'

const models = ref<any[]>([])
const loading = ref(false)

async function loadModels() {
  loading.value = true
  try {
    const res: any = await getModels()
    if (res.code == 200) {
      models.value = res.data || []
    }
  } catch { ElMessage.error('加载模型列表失败') }
  finally { loading.value = false }
}

// ---- 新增模型 ----
const showAddForm = ref(false)
const addForm = ref({
  name: '',
  description: '',
  isRecommended: false,
  isBeta: false,
  status: true,
  sort: 0,
})
const addLoading = ref(false)

function resetAddForm() {
  addForm.value = { name: '', description: '', isRecommended: false, isBeta: false, status: true, sort: 0 }
  showAddForm.value = false
}

async function handleAddModel() {
  if (!addForm.value.name.trim()) {
    ElMessage.warning('请输入模型名称')
    return
  }
  addLoading.value = true
  try {
    const res: any = await addModel({ ...addForm.value, name: addForm.value.name.trim() })
    if (res.code == 200) {
      ElMessage.success('新增模型成功')
      resetAddForm()
      loadModels()
    } else {
      ElMessage.error(res.msg || '新增失败')
    }
  } catch { ElMessage.error('新增模型失败') }
  finally { addLoading.value = false }
}

// ---- 删除模型 ----
async function handleDeleteModel(model: any) {
  try {
    await ElMessageBox.confirm(`确定要删除模型 "${model.name}" 吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch { return }

  try {
    const res: any = await deleteModel(model.id)
    if (res.code == 200) {
      ElMessage.success('删除成功')
      loadModels()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch { ElMessage.error('删除模型失败') }
}

onMounted(() => { loadModels() })
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>模型管理</h1>
      <button class="btn-primary" @click="showAddForm = !showAddForm">
        {{ showAddForm ? '取消' : '新增模型' }}
      </button>
    </div>

    <!-- 新增模型表单 -->
    <div v-if="showAddForm" class="add-form-card">
      <h3>新增模型</h3>
      <div class="form-grid">
        <div class="field">
          <label>模型名称 <span class="required">*</span></label>
          <input v-model="addForm.name" placeholder="例如: qwen-plus" />
        </div>
        <div class="field">
          <label>描述</label>
          <input v-model="addForm.description" placeholder="模型描述信息" />
        </div>
        <div class="field">
          <label>排序</label>
          <input v-model.number="addForm.sort" type="number" placeholder="数字越大越靠前" />
        </div>
        <div class="field row">
          <label class="checkbox-label">
            <input type="checkbox" v-model="addForm.isRecommended" />
            推荐模型
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="addForm.isBeta" />
            Beta 版本
          </label>
          <label class="checkbox-label">
            <input type="checkbox" v-model="addForm.status" />
            启用
          </label>
        </div>
      </div>
      <div class="form-actions">
        <button class="btn-primary" :disabled="addLoading" @click="handleAddModel">
          {{ addLoading ? '提交中...' : '确认新增' }}
        </button>
        <button class="btn-cancel" @click="resetAddForm">取消</button>
      </div>
    </div>

    <!-- 模型列表 -->
    <div v-loading="loading">
      <table v-if="models.length" class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>模型名称</th>
            <th>描述</th>
            <th>推荐</th>
            <th>Beta</th>
            <th>状态</th>
            <th>排序</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in models" :key="m.id">
            <td>{{ m.id }}</td>
            <td class="mono">{{ m.name }}</td>
            <td class="ellipsis">{{ m.description || '-' }}</td>
            <td>
              <span v-if="m.isRecommended" class="badge badge-rec">推荐</span>
              <span v-else>-</span>
            </td>
            <td>
              <span v-if="m.isBeta" class="badge badge-beta">Beta</span>
              <span v-else>-</span>
            </td>
            <td>
              <span :class="m.status ? 'status-on' : 'status-off'">
                {{ m.status ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ m.sort }}</td>
            <td>
              <button class="btn-delete" @click="handleDeleteModel(m)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <el-empty v-else description="暂无模型数据" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }

.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem;
  h1 { font-size: 1.25rem; margin: 0; }
}

.btn-primary {
  padding: 0.5rem 1.25rem; background: #007CF0; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.btn-cancel {
  padding: 0.5rem 1.25rem; background: transparent; color: var(--text-color); border: 1px solid var(--border-color); border-radius: 0.375rem; cursor: pointer; font-size: 0.875rem; margin-left: 0.5rem;
}

// ---- 新增表单 ----
.add-form-card {
  background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.75rem; padding: 1.5rem; margin-bottom: 1.5rem;
  h3 { font-size: 1rem; margin-bottom: 1rem; }
}
.form-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem;
}
.field {
  display: flex; flex-direction: column; gap: 0.375rem;
  label { font-size: 0.875rem; color: var(--text-color); .required { color: #ef4444; } }
  input[type="text"], input[type="number"] {
    padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); font-size: 0.875rem;
  }
  &.row { flex-direction: row; align-items: center; gap: 1.5rem; }
}
.checkbox-label {
  display: flex; align-items: center; gap: 0.375rem; cursor: pointer; font-size: 0.875rem; color: var(--text-color);
  input[type="checkbox"] { width: 1rem; height: 1rem; cursor: pointer; }
}
.form-actions { margin-top: 1rem; }

// ---- 表格 ----
.data-table {
  width: 100%; border-collapse: collapse; font-size: 0.875rem;
  th { text-align: left; padding: 0.75rem; background: var(--card-bg); border-bottom: 2px solid var(--border-color); font-weight: 500; color: #888; white-space: nowrap; }
  td { padding: 0.75rem; border-bottom: 1px solid var(--border-color); color: var(--text-color); }
  tr:hover td { background: rgba(0,124,240,0.03); }
  .mono { font-family: monospace; font-size: 0.8125rem; }
  .ellipsis { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

.badge { padding: 0.15rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 500; color: #fff; }
.badge-rec { background: #10b981; }
.badge-beta { background: #f59e0b; }

.status-on { color: #10b981; font-weight: 500; }
.status-off { color: #ef4444; font-weight: 500; }

.btn-delete {
  padding: 0.25rem 0.75rem; background: transparent; color: #ef4444; border: 1px solid #ef4444; border-radius: 0.25rem; cursor: pointer; font-size: 0.8125rem; transition: all 0.2s;
  &:hover { background: #ef4444; color: #fff; }
}
</style>
