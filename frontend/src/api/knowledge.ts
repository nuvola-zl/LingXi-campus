import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { getApiBaseURL } from '@/utils/apiConfig'

const BASE_URL = getApiBaseURL()

export function createLibrary(data: { name: string; description: string; type: string }) {
  return request.post('/astra/libraries', data)
}

export function getLibraries() {
  return request.get('/astra/libraries')
}

export function getLibrary(id: number) {
  return request.get(`/astra/libraries/${id}`)
}

export function updateLibrary(id: number, data: any) {
  return request.put(`/astra/libraries/${id}`, data)
}

export function deleteLibrary(id: number) {
  return request.delete(`/astra/libraries/${id}`)
}

export function toggleLibraryTop(id: number) {
  return request.put(`/astra/libraries/${id}/toggle-top`)
}

export function uploadMedia(file: File, libraryId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('libraryId', String(libraryId))
  return request.post('/astra/media', formData)
}

export function getMediaList(libraryId: number) {
  return request.get(`/astra/libraries/${libraryId}/media`)
}

export function getMediaStatus(mediaId: number) {
  return request.get(`/astra/media/${mediaId}/status`)
}

export function deleteMedia(id: number) {
  return request.delete(`/astra/media/${id}`)
}

export async function chatWithLibrary(libraryId: number, prompt: string, sessionId?: number, signal?: AbortSignal) {
  const token = getToken()
  const response = await fetch(`${BASE_URL}/astra/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify({ libraryId, prompt, sessionId }),
    signal,
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  return response.body!.getReader()
}

/**
 * 订阅文件解析进度（SSE）
 * event: progress → { mediaId, status, totalChunks, parsedChunks, percent }
 * event: complete → { mediaId, status, totalChunks, parsedChunks }
 * event: error   → { mediaId, status, error }
 */
export async function subscribeParseProgress(
  mediaId: number,
  onProgress: (data: { percent: number; totalChunks: number; parsedChunks: number }) => void,
  onComplete: (data: { totalChunks: number }) => void,
  onError: (error: string) => void,
  signal?: AbortSignal,
) {
  const token = getToken()
  const response = await fetch(`${BASE_URL}/astra/media/${mediaId}/stream`, {
    headers: { Authorization: token || '' },
    signal,
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)

  const reader = response.body!.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      while (buffer.includes('\n\n')) {
        const idx = buffer.indexOf('\n\n')
        const block = buffer.substring(0, idx)
        buffer = buffer.substring(idx + 2)
        const lines = block.split('\n')
        let eventType = ''
        let data = ''
        for (const line of lines) {
          if (line.startsWith('event:')) eventType = line.substring(6).trim()
          else if (line.startsWith('data:')) data = line.substring(5)
        }
        if (!data) continue
        try {
          const parsed = JSON.parse(data)
          if (eventType === 'progress') onProgress(parsed)
          else if (eventType === 'complete') onComplete(parsed)
          else if (eventType === 'error') onError(parsed.error || '解析失败')
        } catch { /* skip malformed JSON */ }
      }
      if (done) break
    }
  } finally {
    reader.releaseLock()
  }
}