<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getServiceCatalogs, createServiceCatalog, updateServiceCatalog, deleteServiceCatalog, refreshCatalogCache } from '@/api/serviceCatalog'
import { ElMessage, ElMessageBox } from 'element-plus'

const catalogs = ref<any[]>([])
const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ name: '', description: '', keywords: '' })

async function load() {
  const res = await getServiceCatalogs()
  catalogs.value = res.data || []
}

function openCreate() { editingId.value = null; form.value = { name: '', description: '', keywords: '' }; showForm.value = true }
function openEdit(c: any) { editingId.value = c.id; form.value = { name: c.name, description: c.description || '', keywords: (c.keywords || []).join(',') }; showForm.value = true }

async function handleSubmit() {
  try {
    const data = { ...form.value, keywords: form.value.keywords.split(',').map((k: string) => k.trim()).filter(Boolean) }
    if (editingId.value) await updateServiceCatalog(editingId.value, data)
    else await createServiceCatalog(data)
    ElMessage.success('保存成功')
    showForm.value = false
    load()
  } catch { ElMessage.error('保存失败') }
}

async function handleDelete(id: number) {
  try { await ElMessageBox.confirm('确定删除？'); await deleteServiceCatalog(id); ElMessage.success('已删除'); load() } catch {}
}

async function handleRefreshCache() {
  try { await refreshCatalogCache(); ElMessage.success('缓存已刷新') } catch { ElMessage.error('刷新失败') }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>服务目录管理</h1>
      <div>
        <button class="btn-primary" @click="openCreate">+ 新增</button>
        <button class="btn-secondary" @click="handleRefreshCache">刷新缓存</button>
      </div>
    </div>
    <div v-if="showForm" class="form-card">
      <input v-model="form.name" placeholder="名称" />
      <input v-model="form.description" placeholder="描述" />
      <input v-model="form.keywords" placeholder="关键词(逗号分隔)" />
      <button class="btn-primary" @click="handleSubmit">保存</button>
      <button class="btn-cancel" @click="showForm = false">取消</button>
    </div>
    <div class="catalog-list">
      <div v-for="c in catalogs" :key="c.id" class="catalog-item">
        <div><strong>{{ c.name }}</strong><span class="desc">{{ c.description }}</span></div>
        <div class="actions">
          <button @click="openEdit(c)">编辑</button>
          <button class="btn-del" @click="handleDelete(c.id)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; h1 { font-size: 1.25rem; } }
.btn-primary { padding: 0.375rem 0.875rem; background: #007CF0; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; margin-left: 0.5rem; }
.btn-secondary { padding: 0.375rem 0.875rem; background: transparent; color: #007CF0; border: 1px solid #007CF0; border-radius: 0.375rem; cursor: pointer; margin-left: 0.5rem; }
.btn-cancel { padding: 0.375rem 0.875rem; background: transparent; border: 1px solid var(--border-color); color: var(--text-color); border-radius: 0.375rem; margin-left: 0.5rem; cursor: pointer; }
.form-card { padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; margin-bottom: 1rem; display: flex; gap: 0.5rem; input { padding: 0.375rem; border: 1px solid var(--border-color); border-radius: 0.25rem; background: var(--bg-color); color: var(--text-color); flex: 1; } }
.catalog-item { display: flex; justify-content: space-between; align-items: center; padding: 0.75rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.375rem; margin-bottom: 0.375rem; font-size: 0.875rem; .desc { color: #888; margin-left: 1rem; } button { padding: 0.25rem 0.5rem; border: 1px solid #007CF0; background: transparent; color: #007CF0; border-radius: 0.25rem; cursor: pointer; margin-left: 0.25rem; } .btn-del { border-color: rgba(255,77,79,0.3); color: #ff4d4f; } }
</style>