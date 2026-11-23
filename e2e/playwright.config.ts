import { defineConfig } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

/**
 * Browser tests for the claim, custody and staff-review flows.
 *
 * The suite starts its OWN backend (port 8090, in-memory H2, "e2e" profile that seeds two admins)
 * and its OWN Vite dev server (port 5180, proxying to 8090). It never touches the developer's
 * running app (8080/5173) or the PostgreSQL dev database, and every run starts from an empty DB.
 *
 * It uses the Microsoft Edge that ships with Windows (channel "msedge"), so no browser download is
 * needed. All temporary files (browser profiles, Maven/JVM temp) go to e2e/.tmp on this drive.
 */
const API_PORT = 8090
const WEB_PORT = 5180
const root = path.resolve(__dirname, '..')
const tmp = path.join(__dirname, '.tmp')
fs.mkdirSync(tmp, { recursive: true })
// Browser profiles are created in os.tmpdir(); keep them off a full system drive.
process.env.TMP = tmp
process.env.TEMP = tmp
process.env.TMPDIR = tmp

// Absolute path: the Windows shell used by Playwright does not search the working directory.
const mvnw = `"${path.join(root, 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge',
    headless: process.env.E2E_HEADED !== '1',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: [
    {
      // useTestClasspath adds test dependencies (the H2 driver); the compiled test classes (the e2e admin
      // seed in src/test) must be added explicitly. Offline: uses the local Maven repository.
      command: `${mvnw} -o -q spring-boot:run "-Dspring-boot.run.useTestClasspath=true"`
        + ` "-Dspring-boot.run.additional-classpath-elements=${path.join(root, 'backend', 'target', 'test-classes')}"`
        + ` "-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=${tmp}"`,
      cwd: path.join(root, 'backend'),
      url: `http://localhost:${API_PORT}/api/auth/csrf`,
      timeout: 240_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        SERVER_PORT: String(API_PORT),
        SPRING_PROFILES_ACTIVE: 'e2e',
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:findback-e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1',
        SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
        SPRING_DATASOURCE_USERNAME: 'sa',
        SPRING_DATASOURCE_PASSWORD: '',
        SPRING_JPA_HIBERNATE_DDL_AUTO: 'create-drop',
        MAVEN_OPTS: `-Djava.io.tmpdir=${tmp}`,
        TMP: tmp,
        TEMP: tmp,
      },
    },
    {
      command: `node node_modules/vite/bin/vite.js --port ${WEB_PORT} --strictPort`,
      cwd: path.join(root, 'frontend'),
      url: `http://localhost:${WEB_PORT}`,
      timeout: 90_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
      env: { FINDBACK_API_TARGET: `http://localhost:${API_PORT}`, TMP: tmp, TEMP: tmp },
    },
  ],
})
