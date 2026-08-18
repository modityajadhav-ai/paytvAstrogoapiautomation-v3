# API Automation Framework

Maven-based API test automation using **REST Assured**, **TestNG**, **Allure**, and **Logback**. Sample tests target the public [ReqRes](https://reqres.in/) API so you can run the suite without credentials.

## Prerequisites

- **JDK 17+** (the project compiles with `--release 17`)
- **Maven 3.9+** *or* use the included **Maven Wrapper** (no global Maven install)

### Windows: `mvn` is not recognized

Use the wrapper from the project root (downloads Maven on first run):

```powershell
cd c:\API-Automation
```

Set **`JAVA_HOME`** to a **JDK 17** install (not only JRE). The wrapper script requires `JAVA_HOME`; if it points to JDK 11 you will see `release version 17 not supported`.

Example (adjust the path to your JDK 17 folder):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd -version
```

In PowerShell, quote JVM properties so `-D` is not parsed incorrectly:

```powershell
.\mvnw.cmd clean test "-Dtest=com.automation.api.tests.UserApiTest"
```

### Linux / macOS

```bash
export JAVA_HOME=/path/to/jdk-17
./mvnw clean test '-Dtest=com.automation.api.tests.UserApiTest'
```

## Project layout

| Path | Purpose |
|------|---------|
| `src/main/java/.../config` | Environment loading (`test`, `dev`, `stage`, `load`, `prod`) |
| `src/main/java/.../client` | REST Assured API clients |
| `src/main/java/.../model` | Request/response POJOs |
| `src/main/java/.../util` | JSON, reporting, property helpers |
| `src/test/java/.../tests` | TestNG test classes |
| `src/test/java/.../dataprovider` | TestNG `@DataProvider` sources |
| `src/test/resources/environments` | Per-environment properties |
| `Jenkinsfile` | Jenkins pipeline |
| `.github/workflows/api-ci.yml` | GitHub Actions workflow |

## Run tests

Use **`mvn`** if installed, otherwise **`./mvnw`** / **`.\mvnw.cmd`** as above.

```bash
# Default: Maven profile `test` → environments/test.properties
mvn clean test
# or: ./mvnw clean test

# VRGO / other stacks (each file under src/test/resources/environments/)
mvn clean test -P dev
mvn clean test -P stage
mvn clean test -P load
mvn clean test -P prod
```

Override without changing the active Maven profile:

```bash
mvn clean test -Denv=dev
mvn clean test -Denv=stage
mvn clean test -Denv=prod
```

Or set OS env `ENV` to `dev`, `stage`, `prod`, `load`, or `test`.

### VRGO URLs and content ids

| Environment | Properties file | `vrgo.base.url` (default in repo) |
|-------------|-----------------|-----------------------------------|
| test | `environments/test.properties` | `https://api.vrgo.test.xp.irdeto.com` |
| dev | `environments/dev.properties` | `https://api.vrgo.dev.xp.irdeto.com` |
| stage | `environments/stage.properties` | `https://api.vrgo.astro.stage.xp.irdeto.com` |
| prod | `environments/prod.properties` | `https://api.vrptv.ctrp.astro.com.my` |

Per-environment headers and catalogue ids live under the same files as `vrgo.header.*`. Adjust `vrgo.header.catalogueids`, `origin`, `referer`, etc. for each stack.

**Continue-watch read** uses `vrgo.continue.watch.path` (subscriber-event). **Add to CW** uses `vrgo.subscriber.continue.watch.path` (subscriber-activity-producer) plus optional `vrgo.cw.add.movie.*` defaults for tests.

**Subscriber CW POST — per-kind IDs (change with environment):** for each stack, edit the same keys in that stack’s file only:

| Property pattern | Example (movie) | Example (TV show) |
|------------------|-----------------|-------------------|
| `vrgo.cw.add.<kind>.content.id` | `vrgo.cw.add.movie.content.id` | `vrgo.cw.add.tvshow.content.id` |
| `vrgo.cw.add.<kind>.content.type` | `VOD` | `TV_SHOW` (adjust if your API expects another value) |
| `vrgo.cw.add.<kind>.watch.duration` | `2` | `2` |

`<kind>` is one of `movie`, `tvshow`, `series`, `boxset`. In code use `CwAddContentKind`. Tests skip a kind when its `content.id` is blank or starts with `REPLACE`.

Content layout identifiers (Movie, TVShow, Series, Boxset) are configured as `vrgo.content.movie`, `vrgo.content.tvshow`, `vrgo.content.series`, `vrgo.content.boxset` so values can differ per environment. In Java, resolve them with `VrgoContentKind.MOVIE.resolve(config)` (see `ContinueWatch` Allure parameters for an example).

**Production:** do not commit bearer tokens or `x-api-key`. `prod.properties` omits `vrgo.x.api.key`; supply `VRGO_X_API_KEY` (and bearer) via CI or local env.

### VRGO auth (all environments)

Each stack has its own secrets file, token cache, and browser login URL:

| Environment | Secrets file | Token cache | Browser login (`vrgo.auth.browser.url`) |
|-------------|--------------|-------------|----------------------------------------|
| test | `secrets/vrgo-auth.test.local.properties` | `vrgo-token-cache-test.json` | `https://web.vrgo.test.xp.irdeto.com/hubMovies` |
| dev | `secrets/vrgo-auth.dev.local.properties` | `vrgo-token-cache-dev.json` | `https://web.vrgo.dev.xp.irdeto.com/hubHome` |
| load | `secrets/vrgo-auth.load.local.properties` | `vrgo-token-cache-load.json` | `https://web.vrgo.load.xp.irdeto.com/hubHome` |
| stage | `secrets/vrgo-auth.stage.local.properties` | `vrgo-token-cache-stage.json` | `https://web.vr.ctrp-stag.stgbpkastro.com/hubHome` |
| stage2 | `secrets/vrgo-auth.stage2.local.properties` | `vrgo-token-cache-stage2.json` | `https://web2.vr.ctrp-stag.stgbpkastro.com/hubHome` |
| prod | `secrets/vrgo-auth.prod.local.properties` | `vrgo-token-cache-prod.json` | `https://vrptv.ctrp.astro.com.my/hubHome` |

Copy the matching `secrets/vrgo-auth.<env>.local.properties.example` → remove `.example`, then set `vrgo.refresh.token`, `vrgo.auth.username`, and `vrgo.auth.password` for that stack. Legacy `secrets/vrgo-auth.local.properties` still works as a fallback.

**CI variables** (masked): `VRGO_REFRESH_TOKEN_<ENV>` or generic `VRGO_REFRESH_TOKEN` per job; optional `VRGO_AUTH_USERNAME_<ENV>` / `VRGO_AUTH_PASSWORD_<ENV>` for browser recovery.

```powershell
# Run load profile
.\mvnw.cmd clean test -Pload
# Or: scripts\run-tests.bat load
```

## Allure report

Tests write raw results under `target/allure-results` (see `src/test/resources/allure.properties` and the Surefire `allure.results.directory` property).

After **`mvn test`** (including `mvn test "-Dtest=ContinueWatch"`), the POM also runs **`allure:report`** in the same lifecycle, so the HTML report is generated at **`target/site/allure-maven/index.html`** without a second command.

**Old and new class names both in the report:** Allure merges every `*.json` under `target/allure-results`. Runs from before a rename (e.g. `Get_CW`) stay until you clear that folder. Run **`mvn clean test`** (or delete `target/allure-results` before `mvn test` / `mvn allure:report`) so only the current suite appears.

To skip HTML generation (faster runs, e.g. CI that only archives `allure-results`):

```powershell
mvn clean test -Dallure.report.skip=true
```

To build only the report from existing results:

```powershell
mvn allure:report
```

Or open a local server (downloads the Allure CLI on first use):

```powershell
mvn allure:serve
```

The Allure Maven plugin uses a fixed CLI version from GitHub (`allure.report.version` and `allureDownloadUrl` in `pom.xml`) because some Allure library patch releases do not publish a matching command-line zip on Maven Central.

## CI

- **GitHub Actions**: push or PR to `main` runs `mvn clean test` and uploads Allure results as an artifact.
- **Jenkins**: use the included `Jenkinsfile`; configure a JDK 17 tool named `JDK17` (or adjust the `tools` block).

## Daily schedule + email reports

Three options — pick the one that matches where you run tests.

### Option A — GitHub Actions (recommended)

Workflow: `.github/workflows/api-daily.yml` runs every day at **7:00 AM IST** (`30 1 * * *` UTC). Change the `cron` line if you need another timezone.

**One-time setup** — add these [repository secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions):

| Secret | Purpose |
|--------|---------|
| `SMTP_SERVER` | e.g. `smtp.office365.com` |
| `SMTP_PORT` | e.g. `587` |
| `SMTP_USERNAME` | SMTP login |
| `SMTP_PASSWORD` | SMTP password or app password |
| `SMTP_FROM` | Sender address |
| `REPORT_RECIPIENTS` | Comma-separated team emails |
| `VRGO_BEARER_TOKEN` | Bearer JWT for API calls |
| `VRGO_X_API_KEY` | x-api-key header value |

Emails attach **Extent HTML** (`target/extent-reports/ExtentReport.html`) and the latest **Excel** report. Allure raw results are kept as a workflow artifact for 30 days.

Trigger manually: **Actions → API Daily Regression → Run workflow**.

### Option B — Windows Task Scheduler (local machine)

```powershell
copy scripts\.env.example scripts\.env
# Edit scripts\.env with SMTP settings and recipient list

# Register daily 7:00 AM task (run once)
.\scripts\register-daily-task.ps1

# Or run immediately
.\scripts\run-daily-tests-and-email.ps1
```

The PC must be on at 7 AM, or enable **Start when available** in Task Scheduler (already set in the script).

### Option C — Jenkins

The `Jenkinsfile` includes `cron('0 7 * * *')` (7 AM **Jenkins server** local time) and `emailext` with Extent + Excel attachments.

Prerequisites:

1. **Email Extension** plugin installed.
2. Jenkins **Manage Jenkins → System**: configure SMTP + **Default Recipients**.
3. Credentials `vrgo-bearer-token` and `vrgo-x-api-key` (or bind `VRGO_BEARER_TOKEN` / `VRGO_X_API_KEY` in the job’s **Environment** section).

## Extending the framework

1. Add endpoints in `com.automation.api.constants.ApiEndpoints`.
2. Add POJOs under `com.automation.api.model`.
3. Add client methods in `com.automation.api.client` (extend `BaseApiClient`).
4. Add DataProviders under `com.automation.api.dataprovider`.
5. Point `base.url` in `src/test/resources/environments/*.properties` at your service.

Never commit secrets; use CI variables or Jenkins credentials for tokens.
