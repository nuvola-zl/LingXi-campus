import { getToken } from '@/utils/auth'
import { getApiBaseURL } from '@/utils/apiConfig'
import { parseSSEEvent } from '@/utils/sseParser'

const BASE_URL = getApiBaseURL()

export interface SSEChunk {
  type: 'content' | 'ai_prompt' | 'image_url' | 'done' | 'error'
  text?: string
  error?: string
}

export const chatAPI = {
  async *sendMessage(
    formData: FormData,
    sessionId: number | null,
    onSessionCreated?: (sessionId: string) => void,
    signal?: AbortSignal,
  ): AsyncGenerator<SSEChunk> {
    const token = getToken()
    const headers: Record<string, string> = { Authorization: token || '' }

    if (sessionId) formData.append('sessionId', String(sessionId))
    if (!formData.has('enableThinking')) formData.append('enableThinking', 'true')

    const response = await fetch(`${BASE_URL}/ai/text-chat`, {
      method: 'POST',
      headers,
      body: formData,
      signal,
    })

    if (!response.ok) {
      if (response.status === 401) throw new Error('请先登录')
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const appendBuffer = (text: string) => {
      buffer += text.replace(/\r\n/g, '\n')
    }

    try {
      while (true) {
        const { value, done } = await reader.read()
        if (done) {
          appendBuffer(decoder.decode())
          if (buffer.trim()) {
            const result = parseSSEEvent(buffer, onSessionCreated)
            if (result.error) { yield { type: 'error', error: result.error }; break }
            if (result.content) yield { type: 'content', text: result.content }
          }
          break
        }

        appendBuffer(decoder.decode(value, { stream: true }))

        while (buffer.includes('\n\n')) {
          const idx = buffer.indexOf('\n\n')
          const eventBlock = buffer.substring(0, idx)
          buffer = buffer.substring(idx + 2)

          const parsed = parseSSEEvent(eventBlock, onSessionCreated)
          if (parsed.error) { yield { type: 'error', error: parsed.error }; return }
          if (parsed.event === 'AI_PROMPT') { yield { type: 'ai_prompt', text: parsed.content } }
          else if (parsed.event === 'IMAGE_URL') { yield { type: 'image_url', text: parsed.content } }
          else if (parsed.event === 'DONE') { yield { type: 'done' } }
          else if (parsed.content) { yield { type: 'content', text: parsed.content } }
        }
      }
    } finally {
      reader.releaseLock()
    }
  },

  async getChatHistory(type: string) {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/ai/session/list?type=${encodeURIComponent(type)}&pageSize=100`, {
      headers: { Authorization: token || '' },
    })
    const result = await response.json()
    if (result.code == 200 && result.data) {
      return result.data.map((s: any) => ({
        id: s.id,
        title: s.title || '新对话',
        type: s.type,
        lastActiveAt: s.lastActiveAt,
      }))
    }
    return []
  },

  async getChatMessages(sessionId: number) {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/ai/history/session/${sessionId}`, {
      headers: { Authorization: token || '' },
    })
    const result = await response.json()
    if (result.code == 200 && result.data) {
      return result.data.map((msg: any) => ({
        role: msg.role,
        content: msg.content,
        timestamp: msg.createdAt ? new Date(msg.createdAt) : new Date(),
        metadata: msg.metadataJson,
      }))
    }
    return []
  },

  async getModelList() {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/models?_t=${Date.now()}`, {
        headers: { Authorization: token || '' },
        cache: 'no-cache',
      })
      if (!response.ok) {
        console.error('[getModelList] HTTP error:', response.status)
        return []
      }
      const result = await response.json()
      if (result.code == 200 && result.data) {
        return result.data.map((m: any) => ({
          id: m.id,
          name: m.name,
          value: m.name,
          description: m.description,
          recommended: m.isRecommended,
          beta: m.isBeta,
          sort: m.sort,
          status: m.status,
        }))
      }
      console.warn('[getModelList] Unexpected response:', result)
      return []
    } catch (e) {
      console.error('[getModelList] Error:', e)
      return []
    }
  },

  async updateSession(sessionId: number, title?: string, groupId?: number) {
    const token = getToken()
    const params = new URLSearchParams()
    params.append('sessionId', String(sessionId))
    if (title) params.append('title', title)
    if (groupId) params.append('groupId', String(groupId))
    const response = await fetch(`${BASE_URL}/ai/session?${params}`, {
      method: 'PUT',
      headers: { Authorization: token || '' },
    })
    if (!response.ok) throw new Error('更新会话失败')
  },

  async deleteSession(sessionId: number) {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/ai/session?sessionId=${sessionId}`, {
      method: 'DELETE',
      headers: { Authorization: token || '' },
    })
    if (!response.ok) throw new Error('删除会话失败')
  },
}