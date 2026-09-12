import { getToken } from '@/utils/auth'
import { getRootBaseURL } from '@/utils/apiConfig'

const ROOT = getRootBaseURL()

function authHeaders(): Record<string, string> {
  const token = getToken()
  return { 'Content-Type': 'application/json', Authorization: token || '' }
}

function handleResponse(res: any) {
  return res.code == 200 ? res.data : null
}

export async function getPendingApprovals() {
  const res = await fetch(`${ROOT}/admin/manage/pending`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function approvePurchase(orderNo: string) {
  const res = await fetch(`${ROOT}/admin/manage/purchase/${encodeURIComponent(orderNo)}/arrived`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function approveLeave(requestId: number, approved: boolean, remark?: string) {
  const res = await fetch(`${ROOT}/admin/manage/leave/${requestId}/approve`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ approved, remark }),
  })
  return handleResponse(await res.json())
}

export async function approvePunchCorrection(fineId: number, approved: boolean, remark?: string) {
  const res = await fetch(`${ROOT}/admin/manage/fine/${fineId}/approve`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ approved, remark }),
  })
  return handleResponse(await res.json())
}

export async function approvePurchaseRequest(purchaseId: number, approved: boolean, remark?: string) {
  const res = await fetch(`${ROOT}/admin/manage/purchase/${purchaseId}/approve`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ approved, remark }),
  })
  return handleResponse(await res.json())
}

// ===== 设备管理（管理员） =====

export async function confirmHandover(requestNo: string) {
  const res = await fetch(`${ROOT}/admin/manage/device/${encodeURIComponent(requestNo)}/confirmHandover`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function confirmDeviceReturn(returnId: number, remark?: string) {
  const params = remark ? `?remark=${encodeURIComponent(remark)}` : ''
  const res = await fetch(`${ROOT}/admin/manage/device/return/${returnId}/confirm${params}`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function getDeviceInventory() {
  const res = await fetch(`${ROOT}/admin/manage/device/inventory`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function getPendingReturns() {
  const res = await fetch(`${ROOT}/admin/manage/device/returns/pending`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function getPurchaseList(params?: { status?: number; page?: number; size?: number }) {
  const query = new URLSearchParams()
  if (params?.status != null) query.append('status', String(params.status))
  if (params?.page != null) query.append('page', String(params.page))
  if (params?.size != null) query.append('size', String(params.size))
  const qs = query.toString()
  const res = await fetch(`${ROOT}/admin/manage/purchase/list${qs ? '?' + qs : ''}`, { headers: authHeaders() })
  return handleResponse(await res.json())
}