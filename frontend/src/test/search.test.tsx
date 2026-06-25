/**
 * Alumni search and filter tests (UC007, UC008)
 * Tests search input, seniority/industry filters, and empty state message.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import AlumniPage from '../pages/university/AlumniPage'

const { mockGet, mockPut } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPut: vi.fn(),
}))

vi.mock('../lib/api', () => ({
  default: { get: mockGet, put: mockPut },
}))

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 'u1', email: 'admin@utm.edu.my',
      fullName: 'Admin', role: 'admin', tenantId: 't1',
    },
    logout: vi.fn(),
  }),
}))

const ALUMNI_PAGE = {
  content: [
    {
      id: 'a1', tenantId: 't1', fullName: 'Ahmad Hassan',
      linkedinUrl: null, educationEndYear: 2018, universityName: 'UTM',
      createdAt: '2024-01-01',
      profile: {
        id: 'p1', alumniId: 'a1', employer: 'Petronas', jobTitle: 'Engineer',
        seniority: 'Senior', industry: 'Energy', location: 'KL',
        confidenceScore: 0.9, updatedAt: '2024-01-01',
      },
    },
    {
      id: 'a2', tenantId: 't1', fullName: 'Siti Ibrahim',
      linkedinUrl: null, educationEndYear: 2020, universityName: 'UTM',
      createdAt: '2024-01-02',
      profile: {
        id: 'p2', alumniId: 'a2', employer: 'Maybank', jobTitle: 'Analyst',
        seniority: 'Junior', industry: 'Finance', location: 'Selangor',
        confidenceScore: 0.75, updatedAt: '2024-01-02',
      },
    },
  ],
  totalElements: 2, totalPages: 1, size: 20, number: 0,
}

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }

async function renderAndWait() {
  let utils: ReturnType<typeof render>
  await act(async () => {
    utils = render(<BrowserRouter><AlumniPage /></BrowserRouter>)
    await Promise.resolve()
  })
  await waitFor(() => screen.getByPlaceholderText(/search/i), { timeout: 3000 })
  return utils!
}

describe('AlumniPage — search and filter (UC007, UC008)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue({ data: ALUMNI_PAGE })
  })

  it('renders the search input', async () => {
    await renderAndWait()
    expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument()
  })

  it('renders seniority filter with Senior option', async () => {
    await renderAndWait()
    const selects = document.querySelectorAll('select')
    const hasSenior = Array.from(selects).some(s =>
      Array.from(s.options).some(o => o.text === 'Senior')
    )
    expect(hasSenior).toBe(true)
  })

  it('renders industry filter with Technology option', async () => {
    await renderAndWait()
    const selects = document.querySelectorAll('select')
    const hasTech = Array.from(selects).some(s =>
      Array.from(s.options).some(o => o.text === 'Technology')
    )
    expect(hasTech).toBe(true)
  })

  it('displays alumni names from the API response', async () => {
    await renderAndWait()
    await waitFor(() => {
      expect(screen.getByText('Ahmad Hassan')).toBeInTheDocument()
      expect(screen.getByText('Siti Ibrahim')).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('shows an empty-state message when API returns no results', async () => {
    mockGet.mockResolvedValue({ data: EMPTY_PAGE })
    await act(async () => {
      render(<BrowserRouter><AlumniPage /></BrowserRouter>)
      await Promise.resolve()
    })
    await waitFor(() => {
      expect(screen.getByText(/no alumni found/i)).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('passes the search query (query=) as a URL parameter when user types', async () => {
    await renderAndWait()
    const input = screen.getByPlaceholderText(/search/i)

    await act(async () => {
      fireEvent.change(input, { target: { value: 'Ahmad' } })
    })

    // Search is debounced 300ms — wait for the request to fire
    await waitFor(() => {
      const calls = mockGet.mock.calls
      const lastUrl = calls[calls.length - 1]?.[0] as string
      expect(lastUrl).toContain('query=Ahmad')
    }, { timeout: 3000 })
  })

  it('passes the seniority filter as a URL parameter', async () => {
    await renderAndWait()
    const selects = document.querySelectorAll('select')
    const senioritySelect = Array.from(selects).find(s =>
      Array.from(s.options).some(o => o.text === 'Senior')
    ) as HTMLSelectElement
    expect(senioritySelect).toBeDefined()

    await act(async () => {
      fireEvent.change(senioritySelect, { target: { value: 'Senior' } })
    })

    await waitFor(() => {
      const calls = mockGet.mock.calls
      const lastUrl = calls[calls.length - 1]?.[0] as string
      expect(lastUrl).toContain('seniority=Senior')
    }, { timeout: 3000 })
  })
})
