import { reactive } from 'vue'

const STORAGE_KEY = 'fitplan.auth.session'

const loadStoredSession = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.accessToken || !parsed?.expiresAt || Date.parse(parsed.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

const stored = loadStoredSession()

export const authState = reactive({
  accessToken: stored?.accessToken || '',
  expiresAt: stored?.expiresAt || '',
  user: stored?.user || null
})

export const hasValidSession = () =>
  Boolean(authState.accessToken && authState.expiresAt && Date.parse(authState.expiresAt) > Date.now())

export const saveSession = session => {
  authState.accessToken = session.accessToken
  authState.expiresAt = session.expiresAt
  authState.user = session.user
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
    accessToken: session.accessToken,
    expiresAt: session.expiresAt,
    user: session.user
  }))
}

export const clearSession = () => {
  authState.accessToken = ''
  authState.expiresAt = ''
  authState.user = null
  sessionStorage.removeItem(STORAGE_KEY)
}
