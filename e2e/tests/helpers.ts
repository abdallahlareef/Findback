import { expect } from '@playwright/test'
import type { Browser, Page } from '@playwright/test'

/** Matches backend/src/test/java/com/findback/module3/e2e/E2eSeedData.java. */
export const PASSWORD = 'E2e-Password-123'
export const ADMIN_A = 'admin-a@e2e.test'
export const ADMIN_B = 'admin-b@e2e.test'

const runId = Date.now().toString(36)
/** Unique per run, so the suite can be re-run against a server that already has data. */
export const emailFor = (who: string) => `${who}-${runId}@e2e.test`

export const today = () => new Date().toISOString().slice(0, 10)

/** Each actor gets their own browser context, i.e. their own cookies and session. */
export async function openAs(browser: Browser): Promise<Page> {
  const context = await browser.newContext()
  return context.newPage()
}

export async function register(page: Page, name: string, email: string) {
  await page.goto('/register')
  await page.getByLabel('Name', { exact: true }).fill(name)
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(/\/login/)
}

/** Students land on the dashboard; staff accounts are admin-only and land in the admin console. */
export async function login(page: Page, email: string, home: RegExp = /\/dashboard$/) {
  await page.goto('/login')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(home)
}

export async function registerAndLogin(browser: Browser, who: string): Promise<{ page: Page; email: string }> {
  const page = await openAs(browser)
  const email = emailFor(who)
  await register(page, who, email)
  await login(page, email)
  return { page, email }
}

/** Logs in one of the seeded staff accounts. */
export async function loginAsStaff(browser: Browser, email: string): Promise<Page> {
  const page = await openAs(browser)
  await login(page, email, /\/admin$/)
  return page
}

/** JSON API call in the page's session, with the CSRF token the app itself would send. */
export async function api<T = unknown>(page: Page, method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    const csrf = await (await page.request.get('/api/auth/csrf')).json() as { headerName: string; token: string }
    headers[csrf.headerName] = csrf.token
  }
  const response = await page.request.fetch(path, { method, headers, data: body })
  expect(response.ok(), `${method} ${path} -> ${response.status()} ${await response.text()}`).toBeTruthy()
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

type ReportInput = { category: string; colour: string; zone: string; expectedRisk?: 'LOW' | 'MEDIUM' | 'HIGH' }

/** Files a report through the real form and returns its id. Checks the FR-10 risk suggestion if asked. */
export async function fileReport(page: Page, type: 'lost' | 'found', input: ReportInput): Promise<number> {
  await page.goto(`/report/${type}`)
  await page.locator('select[name="categoryChoice"]').selectOption(input.category)
  await page.getByLabel('Colour', { exact: true }).fill(input.colour)
  await page.getByLabel('Campus area').fill(input.zone)
  await page.getByLabel(type === 'found' ? 'Date found' : 'Date last seen').fill(today())
  const risk = page.getByLabel('Item risk level')
  if (input.expectedRisk) {
    // The level is pre-selected from the category; the reporter could still change it.
    await expect(risk).toHaveValue(input.expectedRisk)
  } else {
    await risk.selectOption('MEDIUM')
  }
  await page.getByRole('button', { name: 'Submit report' }).click()
  await expect(page).toHaveURL(/\/my-reports/)

  const mine = await api<{ id: number; type: string; category: string }[]>(page, 'GET', '/api/reports/mine')
  const report = mine.find((r) => r.type === type.toUpperCase() && r.category === input.category)
  expect(report, `${type} report for ${input.category}`).toBeTruthy()
  return report!.id
}

/** Member 2's private baseline evidence (not under test here, so set through the API). */
export async function saveBaseline(page: Page, reportId: number) {
  await api(page, 'POST', `/api/evidence/reports/${reportId}/baseline`, {
    serialSuffix: 'A82F',
    privateMark: 'Blue sticker under the lid',
    accessoryDescription: 'Grey felt sleeve',
  })
}

/** Staff manual assessment after inspecting the item (Member 2's endpoint), giving two matches incl. serial. */
export async function assessTwoMatches(staffPage: Page, claimId: number) {
  const view = await api<{ summary: { claimantVersionId: number; finderVersionId: number } }>(
    staffPage, 'GET', `/api/evidence/staff/claims/${claimId}`)
  await api(staffPage, 'POST', `/api/evidence/staff/claims/${claimId}/assessments`, {
    claimantVersionId: view.summary.claimantVersionId,
    finderVersionId: view.summary.finderVersionId,
    serialOutcome: 'MATCH',
    markOutcome: 'MATCH',
    accessoryOutcome: 'UNKNOWN',
    serialClueGroup: 'serial',
    markClueGroup: 'sticker',
    criticalConflict: false,
    reason: 'Inspected at the desk: serial suffix and sticker match',
  })
}

/** Submits a claim through the student Claims page and returns the new claim id. */
export async function submitClaim(page: Page, lostReportId: number, foundReportId: number): Promise<number> {
  await page.goto('/claims')
  await page.getByLabel('Your lost report *').selectOption(String(lostReportId))
  await page.getByRole('radio', { name: new RegExp(`Found report #${foundReportId} `) }).check()
  await page.getByRole('button', { name: 'Submit ownership claim' }).click()
  const message = page.getByText(/Claim #\d+ submitted/)
  await expect(message).toBeVisible()
  const id = Number((await message.textContent())!.match(/Claim #(\d+)/)![1])
  expect(id).toBeGreaterThan(0)
  return id
}

/** Receives a found item into custody from the admin custody desk. */
export async function receiveAtDesk(staffPage: Page, foundReportId: number, locker: string) {
  await staffPage.goto('/admin/custody')
  await staffPage.getByRole('button', { name: new RegExp(`^#${foundReportId} – `) }).click()
  await staffPage.getByLabel('Storage / locker reference *').fill(locker)
  await staffPage.getByLabel('Condition notes *').fill('Received at the Security Desk')
  await staffPage.getByRole('button', { name: 'Confirm intake' }).click()
  await expect(staffPage.getByText(`Found item #${foundReportId} received into custody at ${locker}.`)).toBeVisible()
}

/** Opens a claim in the admin review queue. */
export async function openInQueue(page: Page, claimId: number) {
  await page.goto('/admin')
  await page.getByRole('button', { name: new RegExp(`^Claim #${claimId} –`) }).click()
}
