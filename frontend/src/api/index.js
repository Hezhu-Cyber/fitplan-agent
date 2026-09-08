const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.PROD ? '/api' : 'http://localhost:8123/api')

const connectSSE = (url, params) => {
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  return new EventSource(fullUrl)
}

export const streamFitnessPlan = (message, chatId) => {
  return connectSSE('/ai/fitness/chat', { message, chatId })
}

export default {
  streamFitnessPlan
}
