# FindBack browser tests (Playwright)

End-to-end tests that drive the real UI in Microsoft Edge for the claim, custody and staff-review
flows (Member 3: FR-06, FR-10, FR-11, FR-12, FR-18, T09).

## What they cover

1. **High-risk laptop.** Finder reports it (form pre-selects High risk). Admin A receives it at the
   **custody desk** before anyone claims it. Owner claims it. Approval is blocked until the HIGH-risk
   evidence rule is met. Admin A gives the **first approval** and cannot give the second. Admin B gives
   the second approval and **hands it over**. The owner sees "Returned to you", and **case history**
   shows every step and who did it. Students cannot open the admin console, and staff accounts are
   **admin-only** (student pages send them back to the admin console).
2. **Reconsideration.** A rejected claimant cannot simply claim again. They send one
   **reconsideration request**; staff see it flagged with the message and reopen the claim; the
   claimant sees it reopened.
3. **Two-person dispute resolution.** Admin A approves a claim and then puts it on hold, which raises
   a dispute on the item. Admin A cannot resolve it; Admin B sees who raised it and why, and resolves it.

Reports are filed through the real form, including the fixed **category dropdown** and the risk level it
pre-selects.

Member 2's evidence steps (baseline, staff manual assessment) are set through the API, since they are
not what this suite tests.

## How it runs (nothing to start by hand)

`playwright.config.ts` starts its **own** servers and stops them afterwards:

| Server | Port | Notes |
|---|---|---|
| Backend | 8090 | `spring-boot:run` with an in-memory H2 database and the `e2e` profile |
| Frontend | 5180 | Vite dev server, proxying `/api` to 8090 |

Your normal app (8080 / 5173) and the PostgreSQL dev database are **not** used or changed. Every run
starts from an empty database.

The `e2e` profile loads `backend/src/test/java/com/findback/module3/e2e/E2eSeedData.java`, which
creates two staff accounts (`admin-a@e2e.test`, `admin-b@e2e.test`, password `E2e-Password-123`). It
lives in test sources, so it is never part of the application build.

The browser is the Microsoft Edge that comes with Windows (`channel: "msedge"`), so no browser download
is needed. Browser profiles and Maven/JVM temp files go to `e2e/.tmp`, not the system drive.

## Run

Prerequisites: JDK 21, Node, `frontend/node_modules` installed, and the backend's Maven dependencies
already downloaded (the backend is started offline with `-o`). Ports 8090 and 5180 must be free.

```powershell
cd e2e
npm install --cache D:\SE_Project\Findback\npm-cache   # first time only
node node_modules/@playwright/test/cli.js test           # or: npm test
```

- Watch it in a visible browser: `$env:E2E_HEADED = "1"` before running, or `npm run test:headed`.
- Use Chrome instead of Edge: `$env:E2E_BROWSER_CHANNEL = "chrome"`.
- After a failure, open the HTML report (screenshots, video, trace): `npm run report`.

The first run takes longer because Maven compiles the backend.
