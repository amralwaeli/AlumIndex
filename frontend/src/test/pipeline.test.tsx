/**
 * Pipeline visualiser tests (UC004, UC005, UC016)
 * Tests file validation and results summary display.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import ImportPage from '../pages/superadmin/ImportPage'

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
}))

vi.mock('../lib/api', () => ({
  default: { get: mockGet, post: mockPost },
}))

const TENANT_LIST = [{ id: 'tid-1', institutionName: 'UTM' }]

/** Creates a File whose .size property returns exactly sizeBytes (Proxy-backed). */
function makeFile(name: string, sizeBytes: number, type = 'text/csv'): File {
  const real = new File([new Uint8Array(Math.min(64, sizeBytes))], name, { type })
  if (real.size === sizeBytes) return real
  return new Proxy(real, {
    get(target, prop) {
      if (prop === 'size') return sizeBytes
      const val = Reflect.get(target, prop, target)
      return typeof val === 'function' ? val.bind(target) : val
    },
  }) as unknown as File
}

/** Renders ImportPage, waits until the tenant <select> is present. */
async function renderAndWait() {
  mockGet.mockResolvedValue({ data: TENANT_LIST })
  // no `id` in the response → page creates no tracking card and never polls
  mockPost.mockResolvedValue({ data: {} })

  await act(async () => {
    render(<BrowserRouter><ImportPage /></BrowserRouter>)
    await new Promise(r => setTimeout(r, 150))
  })
  await waitFor(
    () => expect(document.querySelector('select')).not.toBeNull(),
    { timeout: 4000 },
  )
}

/**
 * Simulates a file being selected via the hidden input WITHOUT triggering
 * the parent drop-zone's onClick (which calls reset()). We set `files` via
 * Object.defineProperty and fire only the `change` event, matching what the
 * browser does after the user picks a file through the OS dialog.
 */
async function upload(file: File) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement
  await act(async () => {
    Object.defineProperty(input, 'files', { value: [file], configurable: true, writable: true })
    fireEvent.change(input)
    // Allow React 18 auto-batched state updates to flush
    await new Promise(r => setTimeout(r, 100))
  })
}

describe('ImportPage — file validation (UC004, UC016)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  // ── 1 ─────────────────────────────────────────────────────────────────────
  it('renders the drop zone and university selector', async () => {
    await renderAndWait()
    expect(screen.getByText(/drop file here/i)).toBeInTheDocument()
    expect(screen.getByText('University')).toBeInTheDocument()
  })

  // ── 2 ─────────────────────────────────────────────────────────────────────
  it('rejects files larger than 50 MB', async () => {
    await renderAndWait()

    const file = makeFile('alumni.csv', 50 * 1024 * 1024 + 1)
    await upload(file)

    await waitFor(
      () => expect(screen.getAllByText(/50 MB limit/i).length).toBeGreaterThan(0),
      { timeout: 6000 },
    )
  }, 10000)

  // ── 3 ─────────────────────────────────────────────────────────────────────
  it('accepts files of exactly 50 MB (no size error)', async () => {
    await renderAndWait()

    const file = makeFile('alumni.csv', 50 * 1024 * 1024)
    await upload(file)

    // Allow pipeline to start (no error message should appear)
    await act(async () => { await new Promise(r => setTimeout(r, 200)) })
    expect(screen.queryByText(/file exceeds 50 mb limit/i)).toBeNull()
  }, 10000)

  // ── 4 ─────────────────────────────────────────────────────────────────────
  it('rejects .pdf files with unsupported extension message', async () => {
    await renderAndWait()

    const file = new File(['pdf content'], 'report.pdf', { type: 'application/pdf' })
    await upload(file)

    await waitFor(
      () => expect(screen.getAllByText(/only csv and excel/i).length).toBeGreaterThan(0),
      { timeout: 6000 },
    )
  }, 10000)

  // ── 5 ─────────────────────────────────────────────────────────────────────
  it('accepts .csv files without an extension error', async () => {
    await renderAndWait()
    await upload(new File(['name,date\nAlice,2024'], 'data.csv', { type: 'text/csv' }))
    expect(screen.queryByText(/only csv and excel/i)).toBeNull()
  })

  // ── 6 ─────────────────────────────────────────────────────────────────────
  it('accepts .xlsx files without an extension error', async () => {
    await renderAndWait()
    await upload(
      new File(
        [new Uint8Array(512)],
        'data.xlsx',
        { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
      ),
    )
    expect(screen.queryByText(/only csv and excel/i)).toBeNull()
  })

  // ── 7 ─────────────────────────────────────────────────────────────────────
  it(
    'shows all 5 result columns on batch completion (total/inserted/updated/unchanged/failed)',
    async () => {
      // 1. Render with tenant list loaded
      await renderAndWait()

      // 2. Override mocks AFTER renderAndWait so they apply to the upload + poll
      const completedBatch = {
        id: 'b-1',
        status: 'completed',
        recordCount: 100,
        processedCount: 100,
        insertedCount: 60,
        updatedCount: 30,
        unchangedCount: 7,
        failedCount: 3,
        errorLog: null,
      }
      mockPost.mockResolvedValue({
        data: { ...completedBatch, status: 'processing', processedCount: 0 },
      })
      mockGet.mockImplementation((url: string) => {
        if (url.includes('import/b-1')) {
          return Promise.resolve({ data: completedBatch })
        }
        return Promise.resolve({ data: TENANT_LIST })
      })

      // 3. Upload a valid CSV
      await upload(new File(['name,date\nAlice,2024'], 'alumni.csv', { type: 'text/csv' }))

      // 4. Pipeline polls every 3 s — wait up to 9 s for the completed state
      await waitFor(
        () => {
          expect(screen.getByText('Total')).toBeInTheDocument()
          expect(screen.getByText('Inserted')).toBeInTheDocument()
          expect(screen.getByText('Updated')).toBeInTheDocument()
          expect(screen.getByText('Unchanged')).toBeInTheDocument()
          expect(screen.getByText('Failed')).toBeInTheDocument()
        },
        { timeout: 9000 },
      )
    },
    14000,
  )
})
