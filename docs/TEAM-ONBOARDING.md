# API Automation — Team Onboarding Guide

This document explains how to set up, configure, and run the **VRGO / Astro GO API automation framework** from scratch.

For framework internals and extending tests, see the root [README.md](../README.md).

---

## Table of contents

1. [Overview](#1-overview)
2. [Repository access](#2-repository-access)
3. [Prerequisites](#3-prerequisites)
4. [First-time setup](#4-first-time-setup)
5. [Authentication](#5-authentication)
6. [Running tests](#6-running-tests)
7. [Test suites and modules](#7-test-suites-and-modules)
8. [Reports](#8-reports)
9. [CI and scheduled runs](#9-ci-and-scheduled-runs)
10. [Troubleshooting](#10-troubleshooting)
11. [Security rules](#11-security-rules)

---

## 1. Overview

| Item | Detail |
|------|--------|
| **Purpose** | Automated API regression for VRGO / Astro GO services |
| **Stack** | Java 17, Maven, REST Assured, TestNG, Allure, Logback |
| **Optional** | Playwright (browser-based token recovery) |
| **Default env** | `test` (Maven profile) |

### Project layout

```
API-Automation-V3/
├── src/main/java/com/automation/api/
│   ├── auth/          # Token management, browser recovery
│   ├── client/        # REST Assured API clients
│   ├── config/        # Environment loading
│   ├── constants/     # Endpoints, content kinds
│   └── model/         # Request/response POJOs
├── src/test/java/com/automation/api/
│   ├── tests/         # TestNG test classes
│   └── listeners/     # Allure, Excel, suite bootstrap
├── src/test/resources/
│   ├── environments/  # Per-env URLs, headers, content IDs
│   ├── testng.xml     # Main regression suite
│   └── testng-*.xml   # Focused suites
├── secrets/           # Local auth files (gitignored)
├── scripts/           # Windows helper scripts
├── docs/              # Documentation (this file)
├── pom.xml
└── README.md
```

---

## 2. Repository access

### GitLab (primary)

```
https://gitlab.intelligrape.net/astrogo-automation/apiautomation.git
```

Ask your team lead for access to the **astrogo-automation** group on GitLab.

### Clone

```powershell
git clone https://gitlab.intelligrape.net/astrogo-automation/apiautomation.git
cd apiautomation
```

### GitHub mirrors (optional)

- `https://github.com/modityajadhav-ai/paytvAstrogoapiautomation-v3.git`
- `https://github.com/modityajadhav-ai/paytvAstrogoapiautomation.git`

---

## 3. Prerequisites

| Requirement | Notes |
|-------------|-------|
| **JDK 17+** | `JAVA_HOME` must point to a JDK install (not JRE only) |
| **Maven** | Optional — use included Maven Wrapper (`mvnw.cmd` / `./mvnw`) |
| **Network** | VPN / corporate network if required for target VRGO stacks |
| **Playwright** | Optional — install via script for automatic browser re-login |

### Verify Java and Maven Wrapper

**Windows (PowerShell):**

```powershell
cd D:\API-Automation-V3
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # adjust to your JDK path
.\mvnw.cmd -version
```

**Linux / macOS:**

```bash
export JAVA_HOME=/path/to/jdk-17
./mvnw -version
```

---

## 4. First-time setup

### Step 1 — Clone and open the project

Use your IDE (IntelliJ IDEA recommended) or work from the terminal.

### Step 2 — Pick an environment

| Profile | Properties file | Typical use |
|---------|-----------------|-------------|
| `test` | `src/test/resources/environments/test.properties` | Default / dev stack |
| `dev` | `environments/dev.properties` | Development |
| `stage` | `environments/stage.properties` | QA / staging |
| `stage2` | `environments/stage2.properties` | Second stage stack |
| `load` | `environments/load.properties` | Load testing stack |
| `prod` | `environments/prod.properties` | Production (restricted) |

Most QA work runs against **`stage`**.

### Step 3 — Create local secrets file

Copy the example template:

```powershell
copy secrets\vrgo-auth.local.properties.example secrets\vrgo-auth.stage.local.properties
```

Edit the new file and fill in credentials. See [Authentication](#5-authentication).

> **Important:** Files matching `secrets/vrgo-auth.*.local.properties` are gitignored. Never commit them.

### Step 4 — (Optional) Install Playwright

Enables automatic browser re-login when refresh tokens expire:

```powershell
scripts\install-playwright.bat
```

### Step 5 — Run a smoke test

```powershell
scripts\run-tests.bat stage
```

If auth is configured correctly, tests should start and produce reports under `target/`.

---

## 5. Authentication

VRGO tests require a **subscriber refresh token**. Some tests (VR Search Proxy guest flows) also need a **guest bearer JWT**.

### Per-environment secrets files

| Environment | Secrets file | Token cache |
|-------------|--------------|-------------|
| test | `secrets/vrgo-auth.test.local.properties` | `vrgo-token-cache-test.json` |
| dev | `secrets/vrgo-auth.dev.local.properties` | `vrgo-token-cache-dev.json` |
| stage | `secrets/vrgo-auth.stage.local.properties` | `vrgo-token-cache-stage.json` |
| stage2 | `secrets/vrgo-auth.stage2.local.properties` | `vrgo-token-cache-stage2.json` |
| load | `secrets/vrgo-auth.load.local.properties` | `vrgo-token-cache-load.json` |
| prod | `secrets/vrgo-auth.prod.local.properties` | `vrgo-token-cache-prod.json` |

Legacy fallback: `secrets/vrgo-auth.local.properties` (still supported).

### Required properties

| Property | Required | Description |
|----------|----------|-------------|
| `vrgo.refresh.token` | **Yes** | Subscriber `refresh_token` from Astro ID login |
| `vrgo.auth.username` | Recommended | Test account email (browser recovery) |
| `vrgo.auth.password` | Recommended | Test account password (browser recovery) |
| `vrgo.auth.browser.recovery.enabled` | Optional | `true` to auto re-login via Playwright |
| `vrgo.search.proxy.guest.bearer.token` | Guest tests only | Guest JWT (~5 min TTL) |
| `vrgo.web.basic.auth.username` | Rare | Portal HTTP basic auth (some stacks) |
| `vrgo.web.basic.auth.password` | Rare | Portal HTTP basic auth (some stacks) |

### How to obtain tokens

#### Subscriber refresh token

1. Open the VRGO web app for your target environment (e.g. stage hub).
2. Log in with a **subscriber** test account (not "Browse as Guest").
3. Open browser DevTools → **Network** tab.
4. Find the auth/token response or any API call.
5. Copy the `refresh_token` value (or the long-lived refresh JWT).
6. Paste into `vrgo.refresh.token=` in your secrets file.

> **Do not** use a guest token as `vrgo.refresh.token`. Guest JWTs have `isGuest: true` and subscriber APIs (e.g. Continue Watch POST) will return 401.

#### Guest bearer token

1. On the web app, choose **Continue As Guest**.
2. DevTools → Network → any VRGO API call → **Authorization** header.
3. Copy the value **after** `Bearer `.
4. Paste into `vrgo.search.proxy.guest.bearer.token=`.
5. Re-paste every ~5 minutes (short TTL).

### Browser login URLs (per environment)

| Environment | Browser URL |
|-------------|-------------|
| test | `https://web.vrgo.test.xp.irdeto.com/hubMovies` |
| dev | `https://web.vrgo.dev.xp.irdeto.com/hubHome` |
| load | `https://web.vrgo.load.xp.irdeto.com/hubHome` |
| stage | `https://web.vr.ctrp-stag.stgbpkastro.com/hubHome` |
| stage2 | `https://web2.vr.ctrp-stag.stgbpkastro.com/hubHome` |
| prod | `https://vrptv.ctrp.astro.com.my/hubHome` |

### Token refresh and cache

- Tokens are cached in `vrgo-token-cache-<env>.json` at the project root (gitignored).
- To force a fresh token: delete the cache file and re-run tests.
- Manual refresh:

```powershell
scripts\refresh-token.bat
```

- Reset all auth caches:

```powershell
scripts\reset-auth-cache.bat
```

### CI / pipeline variables

Instead of local files, set these in GitLab CI, Jenkins, or GitHub Actions:

| Variable | Purpose |
|----------|---------|
| `VRGO_REFRESH_TOKEN` | Generic refresh token |
| `VRGO_REFRESH_TOKEN_STAGE` | Per-env refresh token (suffix = env name, upper case) |
| `VRGO_AUTH_USERNAME_STAGE` | Browser recovery username |
| `VRGO_AUTH_PASSWORD_STAGE` | Browser recovery password |
| `VRGO_X_API_KEY` | API key header (required for prod) |
| `VRGO_BEARER_TOKEN` | Optional bootstrap bearer |
| `VRGO_GUEST_BEARER_TOKEN` | Guest JWT for CI |

---

## 6. Running tests

Always run from the **project root**.

### Full regression suite

```powershell
# Using script (recommended on Windows)
scripts\run-tests.bat stage

# Using Maven directly
.\mvnw.cmd clean test -Pstage
```

### Select environment

```powershell
# Maven profile
.\mvnw.cmd clean test -Pdev
.\mvnw.cmd clean test -Pstage
.\mvnw.cmd clean test -Pload

# System property override
.\mvnw.cmd clean test -Denv=stage

# OS environment variable
$env:ENV = "stage"
.\mvnw.cmd clean test
```

### Single test class

```powershell
.\mvnw.cmd clean test -Pstage "-Dtest=com.automation.api.tests.ContinueWatch"
```

### Custom TestNG suite

```powershell
.\mvnw.cmd test -Pstage "-Dsurefire.suiteXmlFiles=src/test/resources/testng-continue-watch-scenarios.xml"
```

### Helper scripts (Windows)

| Script | What it runs |
|--------|--------------|
| `scripts\run-tests.bat [env]` | Full `testng.xml` suite |
| `scripts\run-tests-no-guest.bat [env]` | Suite without guest-dependent tests |
| `scripts\run-continue-watch-scenarios.bat [env]` | Continue Watch scenarios only |
| `scripts\run-cw-wa-fav-tests.bat [env]` | Continue Watch + Watch Again + Favourites |
| `scripts\run-boxset-binge-tests.bat [env]` | Boxset binge flow |
| `scripts\run-vr-search-proxy.bat [env]` | VR Search Proxy tests |
| `scripts\run-recommendation-proxy.bat [env]` | Recommendation Proxy tests |
| `scripts\refresh-token.bat` | Refresh subscriber token manually |
| `scripts\reset-auth-cache.bat` | Clear token cache files |
| `scripts\install-playwright.bat` | Install Chromium for browser recovery |
| `scripts\verify-browser-recovery.bat` | Verify subscriber browser recovery |
| `scripts\verify-guest-browser-recovery.bat` | Verify guest browser recovery |

Pass the environment as the first argument (default: `test`):

```powershell
scripts\run-tests.bat stage
```

### Skip Allure HTML (faster runs)

```powershell
.\mvnw.cmd clean test -Pstage -Dallure.report.skip=true
```

---

## 7. Test suites and modules

### Main suite (`testng.xml`)

| Test class | Module |
|------------|--------|
| `ContentDetail` | Content detail API |
| `ContinueWatch` | Continue Watch read/write |
| `ContinueWatchScenarios` | Ordered CW scenario flows |
| `WatchAgain` | Watch Again API |
| `WatchlistFavourite` | Watchlist / Favourites |
| `BoxsetBingeWatchScenarios` | Boxset binge + CW/WA flows |
| `LearnAction` | Learn action API |
| `RecommendationProxy` | Recommendation proxy |
| `VRSearchProxy` | VR search proxy (includes guest) |
| `LockChannel` | Lock channel |
| `LastTunedChannel` | Last tuned channel |
| `SearchHistory` | Search history |
| `HomescreenMenu` | Homescreen menu |
| `HomescreenProxy` | Homescreen proxy |
| `ConfigService` | Config service |
| `Footer` | Footer API |

### Focused TestNG suites

| File | Purpose |
|------|---------|
| `testng-continue-watch-scenarios.xml` | CW scenarios (ordered) |
| `testng-cw-wa-fav.xml` | CW + Watch Again + Favourites |
| `testng-boxset-binge.xml` | Boxset binge watch |
| `testng-vr-search-proxy.xml` | VR Search Proxy |
| `testng-recommendation-proxy.xml` | Recommendation Proxy |
| `testng-no-guest.xml` | Full suite, guest tests excluded |

### Environment-specific content IDs

Content IDs, catalogue headers, and API paths differ per stack. Edit the active file under `src/test/resources/environments/`:

- `vrgo.header.*` — request headers
- `vrgo.content.movie`, `vrgo.content.tvshow`, etc. — layout content IDs
- `vrgo.cw.add.<kind>.content.id` — Continue Watch POST content IDs

Tests skip a content kind when its ID is blank or starts with `REPLACE`.

---

## 8. Reports

After `mvn test`, reports are generated automatically.

| Report | Location |
|--------|----------|
| **Allure HTML** | `target/site/allure-maven/index.html` |
| **Allure raw JSON** | `target/allure-results/` |
| **Extent HTML** | `target/extent-reports/ExtentReport.html` |
| **Excel** | `excel-reports/` |

### View Allure in browser

```powershell
.\mvnw.cmd allure:serve
```

### Clean stale Allure results

Old runs can leave renamed test classes in the report. Use:

```powershell
.\mvnw.cmd clean test -Pstage
```

---

## 9. CI and scheduled runs

### GitHub Actions

- Workflow: `.github/workflows/api-daily.yml`
- Schedule: daily 7:00 AM IST
- Manual trigger: **Actions → API Daily Regression → Run workflow**

Required secrets: `SMTP_*`, `REPORT_RECIPIENTS`, `VRGO_REFRESH_TOKEN`, `VRGO_X_API_KEY`, etc.

### Jenkins

- Pipeline: `Jenkinsfile`
- Requires JDK 17 tool named `JDK17` (or adjust the `tools` block)
- Email Extension plugin for report emails

### Local Windows scheduler

```powershell
copy scripts\.env.example scripts\.env
# Edit scripts\.env with SMTP and recipient settings
.\scripts\register-daily-task.ps1
```

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `mvn` not recognized | Maven not installed | Use `.\mvnw.cmd` from project root |
| `release version 17 not supported` | Wrong Java version | Set `JAVA_HOME` to JDK 17 |
| `No VRGO refresh token configured` | Missing secrets file | Create `secrets/vrgo-auth.<env>.local.properties` |
| 401 on subscriber APIs | Guest token used as refresh token | Use Astro ID `refresh_token`, not guest JWT |
| Guest tests fail after ~5 min | Guest JWT expired | Re-paste `vrgo.search.proxy.guest.bearer.token` |
| Browser recovery fails | Playwright not installed | Run `scripts\install-playwright.bat` |
| Device limit errors | Too many registered devices | Set `vrgo.auth.browser.evict.device.on.limit=true` |
| Old test names in Allure | Stale results | Run `mvn clean test` |
| PowerShell `-D` parsing errors | Shell eats `-D` flags | Quote JVM args: `"-Dtest=..."` |

---

## 11. Security rules

1. **Never commit** `secrets/vrgo-auth.*.local.properties`, token cache JSON, or `scripts/.env`.
2. Share credentials only via approved secure storage (1Password, Vault, GitLab masked variables).
3. Do **not** paste tokens or passwords in Slack, email, or tickets.
4. Rotate tokens immediately if accidentally committed or exposed.
5. Use dedicated test accounts — not personal production accounts.
6. Production (`prod` profile) requires extra approval; never hardcode prod secrets in source files.

---

## Quick start checklist

- [ ] GitLab access granted
- [ ] Repo cloned
- [ ] JDK 17 installed, `JAVA_HOME` set
- [ ] `secrets/vrgo-auth.stage.local.properties` created from example template
- [ ] Subscriber refresh token and test account credentials added
- [ ] `scripts\run-tests.bat stage` runs successfully
- [ ] Allure report opens at `target/site/allure-maven/index.html`
- [ ] (Optional) Playwright installed for browser recovery

---

## Getting help

- Framework details: [README.md](../README.md)
- Manual test cases: `docs/qa/manual-testcases/`
- Boxset binge test design: `docs/qa/boxset-binge-watch-cw-wa-test-suite.md`
- Contact: your team's QA / automation lead
