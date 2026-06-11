export function apiBase(): string {
  const fromEnv = import.meta.env.VITE_API_ORIGIN?.trim()
  if (fromEnv) {
    return fromEnv.replace(/\/$/, '')
  }
  if (import.meta.env.DEV) {
    return ''
  }
  return 'http://127.0.0.1:8080'
}
