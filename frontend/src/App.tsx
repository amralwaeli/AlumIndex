import { Routes, Route, Navigate } from 'react-router-dom'
import { Component, type ReactNode } from 'react'
import { AuthProvider } from '@/contexts/AuthContext'
import ProtectedRoute from '@/components/auth/ProtectedRoute'
import LoginPage from '@/pages/LoginPage'

// Super Admin pages
import SuperAdminLayout from '@/layouts/SuperAdminLayout'
import OverviewPage from '@/pages/superadmin/OverviewPage'
import CustomersPage from '@/pages/superadmin/CustomersPage'
import ImportPage from '@/pages/superadmin/ImportPage'
import PermissionsPage from '@/pages/superadmin/PermissionsPage'

// University pages
import UniversityLayout from '@/layouts/UniversityLayout'
import DashboardPage from '@/pages/university/DashboardPage'
import AlumniPage from '@/pages/university/AlumniPage'
import DonorInsightsPage from '@/pages/university/DonorInsightsPage'
import AlertsPage from '@/pages/university/AlertsPage'
import ReportsPage from '@/pages/university/ReportsPage'
import AuditLogPage from '@/pages/university/AuditLogPage'
import SettingsPage from '@/pages/university/SettingsPage'

// Public pages
import RegisterPage from '@/pages/RegisterPage'
import ActivatePage from '@/pages/ActivatePage'

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state: { error: Error | null } = { error: null }
  static getDerivedStateFromError(error: Error) { return { error } }
  render() {
    const { error } = this.state
    if (error) {
      return (
        <div style={{ padding: '2rem', fontFamily: 'monospace', background: '#F5F4EF', color: '#172230', minHeight: '100vh' }}>
          <h1 style={{ color: '#BB3B2E', marginBottom: '1rem' }}>Render Error</h1>
          <pre style={{ whiteSpace: 'pre-wrap', color: '#657182', fontSize: '0.875rem' }}>
            {error.message}
            {'\n\n'}
            {error.stack}
          </pre>
          <button
            onClick={() => this.setState({ error: null })}
            style={{ marginTop: '1rem', padding: '0.5rem 1rem', background: '#8C1D40', color: 'white', border: 'none', borderRadius: '0.375rem', cursor: 'pointer' }}
          >
            Try again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

export default function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register/:token" element={<RegisterPage />} />
          <Route path="/activate/:token" element={<ActivatePage />} />

          {/* Super Admin shell */}
          <Route
            path="/operator"
            element={
              <ProtectedRoute allowedRoles={['superadmin']}>
                <SuperAdminLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="overview" replace />} />
            <Route path="overview" element={<OverviewPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="import" element={<ImportPage />} />
            <Route path="permissions" element={<PermissionsPage />} />
          </Route>

          {/* University shell */}
          <Route
            path="/university"
            element={
              <ProtectedRoute allowedRoles={['admin', 'readonly']}>
                <UniversityLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="alumni" element={<AlumniPage />} />
            <Route path="donors" element={<DonorInsightsPage />} />
            <Route path="alerts" element={<AlertsPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="audit" element={<AuditLogPage />} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>

          {/* Root redirect handled after login */}
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </AuthProvider>
    </ErrorBoundary>
  )
}
