export function getApiBaseURL(): string {
  const envVal = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (envVal !== undefined) {
    const trimmed = envVal.trim()
    if (!trimmed) return import.meta.env.PROD ? '' : ''
    return trimmed.replace(/\/+$/, '')
  }
  if (import.meta.env.PROD) return '/api/v1'
  return 'http://localhost:8080/api/v1'
}

/** 获取不带 /api/v1 的根地址，用于 /hr/**, /admin/**, /api/ticket/** 等非标准前缀 */
export function getRootBaseURL(): string {
  const base = getApiBaseURL()
  return base.replace(/\/api\/v1\/?$/, '')
}