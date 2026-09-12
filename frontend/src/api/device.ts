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

export async function applyDevice(data: { deviceType: string; reason: string }) {
  const res = await fetch(`${ROOT}/dag/device/apply`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(await res.json())
}

export async function checkDeviceStatus(requestNo: string) {
  const res = await fetch(`${ROOT}/dag/device/status/${encodeURIComponent(requestNo)}`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function confirmReceive(requestNo: string) {
  const res = await fetch(`${ROOT}/api/device/${encodeURIComponent(requestNo)}/confirmReceive`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function cancelRequest(requestNo: string) {
  const res = await fetch(`${ROOT}/api/device/${encodeURIComponent(requestNo)}/cancel`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function returnDevice(requestNo: string, reason: string) {
  const params = new URLSearchParams({ reason }).toString()
  const res = await fetch(`${ROOT}/api/device/${encodeURIComponent(requestNo)}/return?${params}`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function getMyRequests() {
  const res = await fetch(`${ROOT}/api/device/my-requests`, { headers: authHeaders() })
  return handleResponse(await res.json())
}