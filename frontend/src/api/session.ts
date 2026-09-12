import request from '@/utils/request'

export function createSession(type: string, title?: string) {
  return request.post('/ai/session/create', null, { params: { type, title } })
}

export function updateSession(sessionId: number, title?: string, groupId?: number) {
  return request.put('/ai/session', null, { params: { sessionId, title, groupId } })
}

export function deleteSession(sessionId: number) {
  return request.delete('/ai/session', { params: { sessionId } })
}

export function getSessionList(type?: string, groupId?: number, page?: number, pageSize?: number) {
  return request.get('/ai/session/list', { params: { type, groupId, page, pageSize } })
}

export function toggleSessionTop(sessionId: number, isTop: boolean) {
  return request.put('/ai/session/toggle-top', null, { params: { sessionId, isTop } })
}