import request from '@/utils/request'

export function getModels() {
  return request.get('/ai/models')
}

export function addModel(data: any) {
  return request.post('/ai/models', data)
}

export function deleteModel(id: number) {
  return request.delete('/ai/models', { params: { id } })
}