export interface SSEEvent {
  content?: string
  error?: string
  event?: string
  sessionId?: string
}

function stripLeadingDataPrefix(data: string): string {
  let result = data
  let prev: string
  do {
    prev = result
    result = result.replace(/^data: ?/gm, '')
  } while (result !== prev)
  return result
}

export function parseSSEEvent(
  eventBlock: string,
  onSessionCreated?: (sessionId: string) => void,
): SSEEvent {
  const lines = eventBlock.split('\n')
  const dataLines: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('data:') || line.startsWith('data: ')) {
      const stripped = line.startsWith('data: ') ? line.substring(6) : line.substring(5)
      dataLines.push(stripped)
    }
  }

  if (dataLines.length === 0) return {}

  let data = dataLines.join('\n')
  data = stripLeadingDataPrefix(data)

  if (data.startsWith('SESSION_CREATED:')) {
    const sessionId = data.substring('SESSION_CREATED:'.length).trim()
    if (onSessionCreated) onSessionCreated(sessionId)
    return { event: 'session_created', sessionId }
  }

  if (data.startsWith('ERROR:')) {
    return { error: data.substring('ERROR:'.length) }
  }

  if (data.startsWith('AI_PROMPT:')) {
    return { event: 'AI_PROMPT', content: data.substring('AI_PROMPT:'.length) }
  }

  if (data.startsWith('IMAGE_URL:')) {
    return { event: 'IMAGE_URL', content: data.substring('IMAGE_URL:'.length) }
  }

  if (data.startsWith('DONE')) {
    return { event: 'DONE' }
  }

  return { content: data }
}