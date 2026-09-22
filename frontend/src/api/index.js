import { authState, clearSession } from '../auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.PROD ? '/api' : 'http://localhost:8123/api')

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const authHeaders = () => {
  const headers = {}
  if (authState.accessToken) headers.Authorization = `Bearer ${authState.accessToken}`
  return headers
}

const readProblem = async response => {
  try {
    const problem = await response.json()
    return problem.detail || problem.title || `请求失败（HTTP ${response.status}）`
  } catch {
    return `请求失败（HTTP ${response.status}）`
  }
}

const requestJson = async (path, options = {}) => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
      ...options.headers
    }
  })
  if (!response.ok) {
    if (response.status === 401) clearSession()
    throw new ApiError(await readProblem(response), response.status)
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return null
  return response.json()
}

const dispatchEvents = (buffer, onEvent) => {
  let start = 0
  for (let index = 0; index < buffer.length; index += 1) {
    if (buffer[index] !== '\n' || buffer[index + 1] !== '\n') continue

    const block = buffer.slice(start, index)
    start = index + 2
    const lines = block.split('\n')
    const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message'
    const data = lines
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).replace(/^ /, ''))
      .join('\n')

    if (data && data !== '[DONE]') onEvent(event, data)
  }
  return buffer.slice(start)
}

export const login = credentials => requestJson('/auth/login', {
  method: 'POST',
  body: JSON.stringify(credentials)
})

export const register = payload => requestJson('/auth/register', {
  method: 'POST',
  body: JSON.stringify(payload)
})

export const logout = async () => {
  try {
    await requestJson('/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}

export const currentUser = () => requestJson('/auth/me')

export const streamFitnessPlan = async (message, chatId, { signal, onChunk, onEvent }) => {
  const response = await fetch(`${API_BASE_URL}/ai/fitness/agent`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authHeaders()
    },
    body: JSON.stringify({ message, chatId }),
    signal
  })

  if (!response.ok) {
    if (response.status === 401) clearSession()
    throw new ApiError(await readProblem(response), response.status)
  }
  if (!response.body) throw new ApiError('浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const handleEvent = (event, data) => {
    if (onEvent) {
      onEvent(event, data)
      return
    }
    if (event === 'message' || event === 'delta' || event === 'answer') onChunk?.(data)
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    buffer = dispatchEvents(buffer, handleEvent)
  }
  buffer += decoder.decode()
  dispatchEvents(buffer + '\n\n', handleEvent)
}

export default {
  login,
  register,
  logout,
  currentUser,
  streamFitnessPlan
}
