import request from '@/utils/request'

export function getServiceCatalogs() {
  return request.get('/service-catalog')
}

export function createServiceCatalog(data: any) {
  return request.post('/service-catalog', data)
}

export function updateServiceCatalog(id: number, data: any) {
  return request.put(`/service-catalog/${id}`, data)
}

export function deleteServiceCatalog(id: number) {
  return request.delete(`/service-catalog/${id}`)
}

export function refreshCatalogCache() {
  return request.post('/service-catalog/refresh-cache')
}