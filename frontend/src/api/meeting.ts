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

export async function getMeetingRooms() {
  const res = await fetch(`${ROOT}/admin/meeting/rooms`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function queryAvailableRooms(data: { date: string; startTime: string; endTime: string }) {
  const params = new URLSearchParams(data).toString()
  const res = await fetch(`${ROOT}/admin/meeting/available?${params}`, { headers: authHeaders() })
  return handleResponse(await res.json())
}

export async function bookRoom(data: { roomId: number; date: string; startTime: string; endTime: string; purpose: string }) {
  const res = await fetch(`${ROOT}/admin/meeting/book`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(await res.json())
}

export async function cancelBooking(bookingId: number) {
  const res = await fetch(`${ROOT}/admin/meeting/cancel/${bookingId}`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(await res.json())
}

export async function getMyBookings() {
  const res = await fetch(`${ROOT}/admin/meeting/list`, { headers: authHeaders() })
  return handleResponse(await res.json())
}