import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { getRootBaseURL } from '@/utils/apiConfig'

const ROOT = getRootBaseURL()

function authHeaders(): Record<string, string> {
  const token = getToken()
  return { 'Content-Type': 'application/json', Authorization: token || '' }
}

// 用户端 IT 工单接口（/api/ticket/**，不在 /api/v1 下）
export async function getUserTicketList(): Promise<any[]> {
  const res = await fetch(`${ROOT}/api/ticket/user/list`, { headers: authHeaders() })
  const json = await res.json()
  return json.code == 200 ? json.data : []
}

export async function getUserTicketDetail(ticketId: number): Promise<any> {
  const res = await fetch(`${ROOT}/api/ticket/user/${ticketId}`, { headers: authHeaders() })
  const json = await res.json()
  return json.code == 200 ? json.data : null
}

// 工程师接口（/api/v1/ticket/engineer/**）
export function getEngineerPending() {
  return request.get('/ticket/engineer/pending')
}

export function getEngineerHistory() {
  return request.get('/ticket/engineer/history')
}

export function getEngineerTicketDetail(ticketId: number) {
  return request.get(`/ticket/engineer/${ticketId}`)
}

export function claimTicket(ticketId: number) {
  return request.post(`/ticket/engineer/${ticketId}/claim`)
}

export function addTicketComment(ticketId: number, content: string) {
  return request.post(`/ticket/engineer/${ticketId}/comment`, { content })
}

export function resolveTicket(ticketId: number) {
  return request.post(`/ticket/engineer/${ticketId}/resolve`)
}