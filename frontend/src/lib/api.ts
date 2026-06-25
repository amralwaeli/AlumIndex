import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081',
  timeout: 30_000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('alumindex_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // a 401 from the login attempt itself must reach the form's error
    // handling — redirecting here reloads the page and wipes the message
    const isLoginAttempt = error.config?.url?.includes('/api/auth/login')
    if (error.response?.status === 401 && !isLoginAttempt) {
      localStorage.removeItem('alumindex_token')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export default api
