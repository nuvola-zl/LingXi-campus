import request from '@/utils/request'

export function createGroup(data: { name: string; userId: number; sort: number }) {
  return request.post('/group', data)
}

export function getGroups() {
  return request.get('/group')
}

export function deleteGroup(id: number) {
  return request.delete(`/group/${id}`)
}

export function updateGroup(id: number, data: { name: string; userId: number; sort: number }) {
  return request.put(`/group/${id}`, data)
}