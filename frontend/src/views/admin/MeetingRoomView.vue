<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMeetingRooms, queryAvailableRooms, bookRoom, cancelBooking, getMyBookings } from '@/api/meeting'
import { ElMessage } from 'element-plus'

const rooms = ref<any[]>([])
const bookings = ref<any[]>([])
const activeTab = ref<'book' | 'my'>('book')
const bookForm = ref({ roomId: 0, date: '', startTime: '', endTime: '', purpose: '' })
const availableRooms = ref<any[]>([])

async function load() {
  const r = await getMeetingRooms()
  rooms.value = r || []
}

async function checkAvailable() {
  if (!bookForm.value.date || !bookForm.value.startTime || !bookForm.value.endTime) return
  const res = await queryAvailableRooms({
    date: bookForm.value.date,
    startTime: bookForm.value.startTime,
    endTime: bookForm.value.endTime,
  })
  availableRooms.value = res || []
}

async function handleBook(roomId: number) {
  try {
    await bookRoom({ ...bookForm.value, roomId })
    ElMessage.success('预订成功')
    bookForm.value = { roomId: 0, date: '', startTime: '', endTime: '', purpose: '' }
    availableRooms.value = []
  } catch { ElMessage.error('预订失败') }
}

async function handleCancel(id: number) {
  try { await cancelBooking(id); ElMessage.success('已取消'); loadBookings() } catch { ElMessage.error('取消失败') }
}

async function loadBookings() {
  const res = await getMyBookings()
  bookings.value = res || []
}

async function switchTab(tab: 'book' | 'my') {
  activeTab.value = tab
  if (tab === 'my') await loadBookings()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>场地预约</h1>
    <div class="tabs">
      <button :class="{ active: activeTab === 'book' }" @click="switchTab('book')">预约</button>
      <button :class="{ active: activeTab === 'my' }" @click="switchTab('my')">我的预约</button>
    </div>

    <div v-if="activeTab === 'book'" class="form-card">
      <div class="row">
        <input v-model="bookForm.date" type="date" />
        <input v-model="bookForm.startTime" type="time" />
        <span>~</span>
        <input v-model="bookForm.endTime" type="time" />
        <input v-model="bookForm.purpose" placeholder="用途（如：小组讨论）" />
        <button class="btn-primary" @click="checkAvailable">查询可用</button>
      </div>
      <div v-if="availableRooms.length" class="avail-list">
        <div v-for="r in availableRooms" :key="r.id" class="avail-item">
          <span>{{ r.name }} ({{ r.capacity }}人)</span>
          <button class="btn-primary" @click="handleBook(r.id)">预约</button>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'my'" class="list">
      <div v-for="b in bookings" :key="b.id" class="item">
        <span>{{ b.roomName }} | {{ b.date }} {{ b.startTime }}-{{ b.endTime }}</span>
        <span class="purpose">{{ b.purpose }}</span>
        <button class="btn-cancel" @click="handleCancel(b.id)">取消</button>
      </div>
      <el-empty v-if="bookings.length === 0" description="暂无预约记录" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page { flex: 1; padding: 1.5rem; overflow-y: auto; h1 { font-size: 1.25rem; margin-bottom: 1rem; } }
.tabs { display: flex; gap: 0.5rem; margin-bottom: 1.5rem;
  button { padding: 0.5rem 1rem; border: 1px solid var(--border-color); background: var(--card-bg); color: var(--text-color); border-radius: 0.375rem; cursor: pointer; &.active { background: var(--brand-from); color: #fff; border-color: var(--brand-from); } }
}
.form-card { padding: 1rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.5rem; }
.row { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; input { padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem; background: var(--bg-color); color: var(--text-color); } }
.btn-primary { padding: 0.5rem 1rem; background: var(--brand-from); color: #fff; border: none; border-radius: 0.375rem; cursor: pointer; }
.btn-cancel { padding: 0.25rem 0.75rem; border: 1px solid rgba(255,77,79,0.3); color: #ff4d4f; background: transparent; border-radius: 0.25rem; cursor: pointer; }
.avail-list { margin-top: 1rem; } .avail-item { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0; border-bottom: 1px solid var(--border-color); }
.item { display: flex; align-items: center; gap: 1rem; padding: 0.75rem; background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 0.375rem; margin-bottom: 0.375rem; font-size: 0.875rem; .purpose { color: #888; } }
</style>