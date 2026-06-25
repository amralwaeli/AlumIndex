export type Role = 'superadmin' | 'admin' | 'readonly'

export interface AuthUser {
  id: string
  email: string
  fullName: string
  role: Role
  tenantId: string | null
}

export interface LoginResponse {
  token: string
  role: Role
  tenantId: string | null
  user: AuthUser
}

// ── Tenant / Customer ──────────────────────────────────────────────────────
export interface Tenant {
  id: string
  institutionName: string
  adminName: string
  adminEmail: string
  subscriptionStatus: 'active' | 'suspended'
  createdAt: string
  userCount?: number
  lastImport?: string | null
}

export interface CustomerRequest {
  id: string
  name: string
  email: string
  institution: string
  jobTitle: string
  status: 'pending' | 'approved' | 'denied'
  submittedAt: string
}

// ── Alumni ─────────────────────────────────────────────────────────────────
export interface AlumniProfile {
  id: string
  alumniId: string
  employer: string | null
  jobTitle: string | null
  seniority: string | null
  industry: string | null
  location: string | null
  confidenceScore: number
  updatedAt: string
}

export interface Alumni {
  id: string
  tenantId: string
  fullName: string
  linkedinUrl: string | null
  educationEndYear: number | null
  universityName: string | null
  createdAt: string
  profile?: AlumniProfile
}

export interface ProfileSnapshot {
  id: string
  alumniId: string
  capturedAt: string
  extractedFields: Record<string, unknown>
}

export type CareerEventType = 'job_change' | 'promotion' | 'employer_change'
export type SignificanceLevel = 'high' | 'medium' | 'low'

export interface CareerEvent {
  id: string
  alumniId: string
  alumniName?: string | null
  eventType: CareerEventType
  oldValue: string | null
  newValue: string | null
  significanceLevel: SignificanceLevel
  detectedAt: string
}

export interface AlumniHistory {
  alumni: Alumni
  profile: AlumniProfile | null
  snapshots: ProfileSnapshot[]
  events: CareerEvent[]
}

// ── Import ─────────────────────────────────────────────────────────────────
export type BatchStatus = 'processing' | 'validated' | 'completed' | 'failed'

export interface ImportBatch {
  id: string
  tenantId: string
  filename: string
  uploadedAt: string
  recordCount: number
  processedCount: number
  insertedCount: number
  updatedCount: number
  unchangedCount: number
  failedCount: number
  status: BatchStatus
  errorLog: unknown[] | null
}

// ── Dashboard / Analytics ──────────────────────────────────────────────────
export interface DashboardMetrics {
  totalAlumni: number
  employmentRate: number
  careerChangeAlerts: number
  highValueProspects: number
  employmentTrend: { month: string; rate: number }[]
  seniorityDistribution: { level: string; count: number }[]
  industrySpread: { industry: string; count: number }[]
  recentEvents: CareerEvent[]
}

// ── Donors ─────────────────────────────────────────────────────────────────
export interface DonorInsight {
  alumniId: string
  fullName: string
  employer: string | null
  givingLikelihood: number
  capacityMin: number
  capacityMax: number
  wealthIndicator: 'high' | 'medium' | 'low'
  employerMatchingAvailable: boolean
  suggestedApproach: string
}

// ── Alerts ─────────────────────────────────────────────────────────────────
export type AlertType = 'job_change' | 'donor_prospect' | 'verification' | 'data_quality' | 'system'

export interface Alert {
  id: string
  alumniId: string | null
  fullName: string | null
  alertType: AlertType
  message: string
  significance: SignificanceLevel
  createdAt: string
}

// ── Permissions ────────────────────────────────────────────────────────────
export interface DataPermission {
  id: string
  tenantId: string
  permissionKey: string
  enabled: boolean
}

// ── Audit ──────────────────────────────────────────────────────────────────
export interface AuditLog {
  id: string
  userId: string
  userEmail: string
  tenantId: string
  actionType: string
  actionDetails: string
  actionTime: string
}

// ── Pagination ─────────────────────────────────────────────────────────────
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
