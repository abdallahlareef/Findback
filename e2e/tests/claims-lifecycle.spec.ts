import { expect, test } from '@playwright/test'
import {
  ADMIN_A, ADMIN_B, assessTwoMatches, fileReport, loginAsStaff, openInQueue, receiveAtDesk, registerAndLogin, saveBaseline,
  submitClaim,
} from './helpers'

/**
 * End-to-end through the real UI: students and two staff members in separate browser sessions.
 * Member 2's evidence steps (baseline, manual assessment) are set through the API because they are
 * not what this suite tests; every Member 3 screen is driven by clicks and typing.
 */

test('high-risk laptop: custody desk, two-person approval, handover and case history', async ({ browser }) => {
  const finder = await registerAndLogin(browser, 'finder')
  const owner = await registerAndLogin(browser, 'owner')
  const adminA = await loginAsStaff(browser, ADMIN_A)
  const adminB = await loginAsStaff(browser, ADMIN_B)
  let foundId = 0
  let lostId = 0
  let claimId = 0

  await test.step('Students cannot open the admin console; staff accounts are admin-only', async () => {
    await finder.page.goto('/admin')
    await expect(finder.page).toHaveURL(/\/dashboard$/)
    for (const studentPage of ['/claims', '/report/lost', '/my-reports', '/dashboard']) {
      await adminA.goto(studentPage)
      await expect(adminA).toHaveURL(/\/admin$/)
    }
    await expect(adminA.getByRole('link', { name: 'Report Lost' })).toHaveCount(0)
  })

  await test.step('Finder reports a found laptop; the form pre-selects High risk', async () => {
    foundId = await fileReport(finder.page, 'found', { category: 'Laptop', colour: 'Silver', zone: 'Main Library', expectedRisk: 'HIGH' })
    await saveBaseline(finder.page, foundId)
  })

  await test.step('Admin A receives it at the custody desk before anyone has claimed it', async () => {
    await adminA.goto('/admin/custody')
    await adminA.getByRole('button', { name: new RegExp(`^#${foundId} – Laptop`) }).click()
    await adminA.getByLabel('Storage / locker reference *').fill('SEC-LOCKER-B12')
    await adminA.getByLabel('Condition notes *').fill('Small scratch on the lid')
    await adminA.getByRole('button', { name: 'Confirm intake' }).click()
    await expect(adminA.getByText(`Found item #${foundId} received into custody at SEC-LOCKER-B12.`)).toBeVisible()
    await expect(adminA.getByText('Received into custody', { exact: true })).toBeVisible()
  })

  await test.step('Owner reports the lost laptop and claims the found one', async () => {
    lostId = await fileReport(owner.page, 'lost', { category: 'Laptop', colour: 'Silver', zone: 'Main Library', expectedRisk: 'HIGH' })
    await saveBaseline(owner.page, lostId)
    claimId = await submitClaim(owner.page, lostId, foundId)
    await expect(owner.page.getByText('Under staff review', { exact: true })).toBeVisible()
  })

  await test.step('Approval is blocked until the HIGH-risk evidence rule is met', async () => {
    await openInQueue(adminA, claimId)
    await expect(adminA.getByText(/HIGH risk needs at least 2 matching private characteristics/)).toBeVisible()
    await expect(adminA.getByRole('button', { name: 'Give first approval (1 of 2)' })).toBeDisabled()
  })

  await test.step('Admin A records the inspection and gives the first of two approvals', async () => {
    await assessTwoMatches(adminA, claimId)
    await openInQueue(adminA, claimId)
    await adminA.getByLabel(/Physical inspection notes/).fill('Serial suffix on the base plate checked')
    await adminA.getByLabel(/Decision reason/).fill('Serial and sticker match the claimant baseline')
    await adminA.getByRole('button', { name: 'Give first approval (1 of 2)' }).click()
    await expect(adminA.getByText(/First approval recorded for claim #/)).toBeVisible()
    // Two-person rule: the same staff member cannot also give the second approval.
    await expect(adminA.getByText(/You gave the first approval/)).toBeVisible()
    await expect(adminA.getByRole('button', { name: 'Give second approval & reserve item' })).toBeDisabled()
  })

  await test.step('Admin B gives the second approval and hands the laptop over', async () => {
    await openInQueue(adminB, claimId)
    await expect(adminB.getByText(/First approval given by Admin A \(e2e\)/)).toBeVisible()
    await adminB.getByLabel(/Decision reason/).fill('Second review agrees with the inspection')
    await adminB.getByRole('button', { name: 'Give second approval & reserve item' }).click()
    await expect(adminB.getByText(`Claim #${claimId} is now APPROVED`)).toBeVisible()

    await adminB.getByLabel('One-time handover code *').fill('ONE-TIME-123')
    await adminB.getByLabel('Identity check performed *').fill('Student ID card matched the account name')
    adminB.once('dialog', (dialog) => dialog.accept())
    await adminB.getByRole('button', { name: 'Release item to owner' }).click()
    await expect(adminB.getByText(/Item handed over to owner/)).toBeVisible()
  })

  await test.step('The owner sees the item as returned', async () => {
    await owner.page.goto('/claims')
    await expect(owner.page.getByText('Returned to you')).toBeVisible()
  })

  await test.step('Case history shows every step and who did it', async () => {
    await adminB.goto(`/admin/history/${claimId}`)
    for (const label of ['Claim submitted', 'Received into custody', 'First approval (HIGH risk, 1 of 2)',
      'Approved & item reserved', 'Released to owner', 'Handed over to owner']) {
      await expect(adminB.getByText(label, { exact: true })).toBeVisible()
    }
    await expect(adminB.getByText('by Admin A (e2e)').first()).toBeVisible()
    await expect(adminB.getByText('by Admin B (e2e)').first()).toBeVisible()
  })
})

test('rejected claimant requests reconsideration instead of claiming again, and staff reopen it', async ({ browser }) => {
  const finder = await registerAndLogin(browser, 'finder2')
  const claimant = await registerAndLogin(browser, 'claimant2')
  const adminA = await loginAsStaff(browser, ADMIN_A)

  const foundId = await fileReport(finder.page, 'found', { category: 'Umbrella', colour: 'Black', zone: 'Cafeteria', expectedRisk: 'LOW' })
  await saveBaseline(finder.page, foundId)
  const lostId = await fileReport(claimant.page, 'lost', { category: 'Umbrella', colour: 'Black', zone: 'Cafeteria', expectedRisk: 'LOW' })
  await saveBaseline(claimant.page, lostId)
  const claimId = await submitClaim(claimant.page, lostId, foundId)

  await test.step('Admin A rejects the claim with a reason', async () => {
    await openInQueue(adminA, claimId)
    await adminA.getByLabel(/Decision reason/).fill('The handle description does not match the item')
    await adminA.getByRole('button', { name: 'Reject claim' }).click()
    await expect(adminA.getByText(`Claim #${claimId} is now REJECTED`)).toBeVisible()
  })

  await test.step('A new claim for the same item is refused and points to reconsideration', async () => {
    await claimant.page.goto('/claims')
    await claimant.page.getByLabel('Your lost report *').selectOption(String(lostId))
    await claimant.page.getByRole('radio', { name: new RegExp(`Found report #${foundId} `) }).check()
    await claimant.page.getByRole('button', { name: 'Submit ownership claim' }).click()
    await expect(claimant.page.getByText(/use Request reconsideration instead/)).toBeVisible()
  })

  await test.step('The claimant sends one reconsideration request', async () => {
    await claimant.page.getByRole('button', { name: 'Request reconsideration' }).click()
    await claimant.page.getByLabel(/What new proof do you have/).fill('I have a photo of me holding this umbrella, torn tag visible')
    await claimant.page.getByRole('button', { name: 'Send to staff' }).click()
    await expect(claimant.page.getByText(`Your reconsideration request for claim #${claimId} was sent to staff.`)).toBeVisible()
    await expect(claimant.page.getByText(/You asked staff to reconsider this claim/)).toBeVisible()
    await expect(claimant.page.getByRole('button', { name: 'Request reconsideration' })).toHaveCount(0)
  })

  await test.step('Staff see the request flagged, read the message and reopen the claim', async () => {
    await adminA.goto('/admin')
    await expect(adminA.getByText(/asked for a rejected claim to be reconsidered/)).toBeVisible()
    await openInQueue(adminA, claimId)
    await expect(adminA.getByText(/I have a photo of me holding this umbrella/)).toBeVisible()
    await adminA.getByLabel(/Decision reason/).fill('New photo evidence is worth a second look')
    await adminA.getByRole('button', { name: 'Reconsider (reopen for review)' }).click()
    await expect(adminA.getByText(`Claim #${claimId} is now UNDER_REVIEW`)).toBeVisible()
  })

  await test.step('The claimant sees the claim reopened', async () => {
    await claimant.page.goto('/claims')
    await expect(claimant.page.getByText('Staff reopened this claim after your reconsideration request.')).toBeVisible()
    await expect(claimant.page.getByText('Under staff review', { exact: true })).toBeVisible()
  })
})

test('a dispute raised by one staff member must be resolved by another', async ({ browser }) => {
  const finder = await registerAndLogin(browser, 'finder3')
  const claimant = await registerAndLogin(browser, 'claimant3')
  const adminA = await loginAsStaff(browser, ADMIN_A)
  const adminB = await loginAsStaff(browser, ADMIN_B)

  const foundId = await fileReport(finder.page, 'found', { category: 'Water bottle', colour: 'Green', zone: 'Gym', expectedRisk: 'LOW' })
  await saveBaseline(finder.page, foundId)
  const lostId = await fileReport(claimant.page, 'lost', { category: 'Water bottle', colour: 'Green', zone: 'Gym', expectedRisk: 'LOW' })
  await saveBaseline(claimant.page, lostId)
  const claimId = await submitClaim(claimant.page, lostId, foundId)
  await receiveAtDesk(adminA, foundId, 'SEC-SHELF-3')

  await test.step('Admin A approves, then puts the approved claim on hold, which raises a dispute', async () => {
    await openInQueue(adminA, claimId)
    await adminA.getByLabel(/Decision reason/).fill('Serial suffix matches')
    await adminA.getByRole('button', { name: 'Approve & reserve item' }).click()
    await expect(adminA.getByText(`Claim #${claimId} is now APPROVED`)).toBeVisible()
    await adminA.getByLabel(/Decision reason/).fill('Another student says this bottle is theirs')
    await adminA.getByRole('button', { name: 'Place on hold' }).click()
    await expect(adminA.getByText(`Claim #${claimId} is now ON_HOLD`)).toBeVisible()
  })

  await test.step('Admin A cannot resolve the dispute they raised', async () => {
    await expect(adminA.getByText('You raised this dispute', { exact: false })).toBeVisible()
    await expect(adminA.getByRole('button', { name: 'Resolve dispute' })).toHaveCount(0)
  })

  await test.step('Admin B sees who raised it and resolves it', async () => {
    await openInQueue(adminB, claimId)
    await expect(adminB.getByText(/Raised by/)).toContainText('Admin A (e2e)')
    await adminB.getByLabel(/How was the dispute resolved/).fill('Checked both students: the other student withdrew their statement')
    await adminB.getByRole('button', { name: 'Resolve dispute' }).click()
    await expect(adminB.getByText(`Dispute on found item #${foundId} resolved.`, { exact: false })).toBeVisible()
  })
})
