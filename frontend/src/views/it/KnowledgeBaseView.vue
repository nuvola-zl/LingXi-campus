<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLibraries, createLibrary, deleteLibrary, toggleLibraryTop } from '@/api/knowledge'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const libraries = ref<any[]>([])
const loading = ref(true)
const showCreate = ref(false)
const createForm = ref({ name: '', description: '', type: 'PERSONAL' })

async function loadLibraries() {
  loading.value = true
  try {
    const res = await getLibraries()
    libraries.value = res.data || []
  } finally { loading.value = false }
}

async function handleCreate() {
  try {
    await createLibrary(createForm.value)
    ElMessage.success('创建成功')
    showCreate.value = false
    createForm.value = { name: '', description: '', type: 'PERSONAL' }
    loadLibraries()
  } catch { ElMessage.error('创建失败') }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该知识库吗？', '确认')
    await deleteLibrary(id)
    ElMessage.success('已删除')
    loadLibraries()
  } catch { /* cancelled */ }
}

async function handleToggleTop(id: number) {
  try {
    await toggleLibraryTop(id)
    ElMessage.success('已切换置顶')
    loadLibraries()
  } catch { ElMessage.error('操作失败') }
}

const sortedLibraries = computed(() => {
  return [...libraries.value].sort((a, b) => {
    if (a.isTop && !b.isTop) return -1
    if (!a.isTop && b.isTop) return 1
    return 0
  })
})

onMounted(loadLibraries)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1>知识库</h1>
      <button v-if="userStore.isAdmin" class="btn-primary" @click="showCreate = true">+ 创建知识库</button>
    </div>

    <div v-if="showCreate" class="create-form">
      <input v-model="createForm.name" placeholder="知识库名称" />
      <input v-model="createForm.description" placeholder="描述" />
      <select v-model="createForm.type">
        <option value="PERSONAL">个人</option>
        <option value="TEAM">团队</option>
      </select>
      <button class="btn-primary" @click="handleCreate">确认创建</button>
      <button class="btn-cancel" @click="showCreate = false">取消</button>
    </div>

    <div class="lib-list" v-loading="loading">
      <div v-for="lib in sortedLibraries" :key="lib.id" class="lib-item" @click="router.push(`/it/knowledge/${lib.id}`)">
        <div class="lib-info">
          <h3>
            <span v-if="lib.isTop" class="top-badge">置顶</span>
            {{ lib.name }}
          </h3>
          <p>{{ lib.description }}</p>
        </div>
        <div class="lib-actions">
          <button v-if="userStore.isAdmin" class="btn-top" @click.stop="handleToggleTop(lib.id)">
            {{ lib.isTop ? '取消置顶' : '置顶' }}
          </button>
          <button v-if="userStore.isAdmin" class="btn-del" @click.stop="handleDelete(lib.id)">删除</button>
        </div>
      </div>
      <el-empty v-if="!loading && libraries.length === 0" description="暂无知识库" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; h1 { font-size: 1.25rem; } }
.btn-primary { padding: 0.5rem 1rem; background: #007CF0; color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; }
.btn-cancel { padding: 0.5rem 1rem; background: transparent; border: 1px solid var(--border-color); border-radius: 0.375rem; margin-left: 0.5rem; cursor: pointer; color: var(--text-color); }
.create-form { padding: 1rem; background: var(--card-bg); border-radius: 0.75rem; margin-bottom: 1rem; display: flex; gap: 0.5rem; flex-wrap: wrap; border: 1px solid var(--border-color);
  input, select { padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); }
}
.lib-item { display: flex; justify-content: space-between; align-items: center; padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; margin-bottom: 0.5rem; cursor: pointer; &:hover { border-color: #007CF0; }
  h3 { font-size: 1rem; margin-bottom: 0.25rem; } p { font-size: 0.8125rem; color: #888; }
}
.btn-del { padding: 0.25rem 0.75rem; border: 1px solid rgba(255,77,79,0.3); color: #ff4d4f; background: transparent; border-radius: 0.25rem; cursor: pointer; }
.btn-top { padding: 0.25rem 0.75rem; border: 1px solid rgba(0,124,240,0.3); color: #007CF0; background: transparent; border-radius: 0.25rem; cursor: pointer; margin-right: 0.375rem; font-size: 0.8125rem; }
.top-badge { font-size: 0.625rem; padding: 1px 4px; background: #f59e0b; color: #fff; border-radius: 2px; margin-right: 0.375rem; vertical-align: middle; }
.lib-actions { display: flex; align-items: center; flex-shrink: 0; }
</style>