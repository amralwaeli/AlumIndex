import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react'
import { useNavigate } from 'react-router-dom'
import api from '@/lib/api'
import type { AuthUser, LoginResponse, Role } from '@/types'

interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  hasRole: (...roles: Role[]) => boolean
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem('alumindex_token'),
  )
  const [isLoading, setIsLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    if (!token) {
      setIsLoading(false)
      return
    }
    api
      .get<AuthUser>('/api/auth/me')
      .then((res) => setUser(res.data))
      .catch(() => {
        localStorage.removeItem('alumindex_token')
        setToken(null)
      })
      .finally(() => setIsLoading(false))
  }, [token])

  const login = useCallback(
    async (email: string, password: string) => {
      const { data } = await api.post<LoginResponse>('/api/auth/login', {
        email,
        password,
      })
      localStorage.setItem('alumindex_token', data.token)
      setToken(data.token)
      setUser(data.user)
      if (data.role === 'superadmin') {
        navigate('/operator/customers')
      } else {
        navigate('/university/dashboard')
      }
    },
    [navigate],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      localStorage.removeItem('alumindex_token')
      setToken(null)
      setUser(null)
      navigate('/login')
    }
  }, [navigate])

  const hasRole = useCallback(
    (...roles: Role[]) => !!user && roles.includes(user.role),
    [user],
  )

  const refreshUser = useCallback(async () => {
    const res = await api.get<AuthUser>('/api/auth/me')
    setUser(res.data)
  }, [])

  return (
    <AuthContext.Provider value={{ user, token, isLoading, login, logout, hasRole, refreshUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
