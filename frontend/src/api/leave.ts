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

export async function submitLeave(data: { type: string; startDate: string; endDate: string; reason: string }) {
  const res = await fetch(`${ROOT}/hr/leave/submit`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(await res.json())
}

export async function getLeaveList() {
  const res = await fetch(`${ROOT}/hr/leave/list`, { headers: authHeaders() })
  return handleResponse(await res.json())
}