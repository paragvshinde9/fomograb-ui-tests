# FomoGrab UI Test Framework

Playwright + Java + TestNG UI automation for [fomograb.com](https://www.fomograb.com) — a
deals-aggregator SPA (React + Vite frontend, Express/MongoDB backend). This is a
standalone repo, separate from the application's own codebase, so the test
suite's lifecycle (versioning, CI, dependencies) doesn't get tangled up with
the app's.

Every locator in this suite was written against the app's real source (labels,
`aria-label`/`title` attributes, class names) rather than guessed — see
"How elements are located" below.

## Prerequisites

- **Java 17+** and nothing else — this project ships the Maven Wrapper
  (`mvnw` / `mvnw.cmd`), so you don't need Maven installed.
- **The FomoGrab app running somewhere.** Either:
  - locally: the frontend (`npm run dev`, Vite on `:5173`) **and** the backend
    (`cd server && npm run dev`, needs a MongoDB connection) both running, or
  - a staging deployment, pointed to via `-Dbase.url=...`.

  Most tests read real product data off the homepage, so an empty database
  means an empty product grid and failing tests — that's the app having no
  data, not a bug in the suite.

## Quick start

```bash
# One-time: download the browser binaries Playwright drives (~400MB)
./mvnw exec:java@install-browsers

# Run the default suite (smoke + regression + auth) against http://localhost:5173
./mvnw test

# Just the fast checks
./mvnw test -Dgroups=smoke

# Against a staging deployment instead of localhost
./mvnw test -Dbase.url=https://staging.fomograb.com

# See the browser while it runs
./mvnw test -Dgroups=smoke -Dheadless=false
```

Auth-dependent tests (login success, submit-deal) need a seeded, non-admin
account — they **skip themselves with a clear message** if these aren't set,
they don't fail the build:

```bash
export FG_TEST_USER_EMAIL="uitests@example.com"      # PowerShell: $env:FG_TEST_USER_EMAIL="..."
export FG_TEST_USER_PASSWORD="whatever-you-seeded"
```

Seed that account once with the app's own script (`node server/seedTestUser.js`
in the fomograb repo) — the suite never creates accounts through the UI itself,
because `server/routes/auth.js` rate-limits signup/login and a real signup also
fires a verification email.

## Project layout

```
src/test/java/com/fomograb/uitests/
├── config/
│   ├── TestConfig.java       # base.url / browser / headless / timeouts — all overridable
│   └── TestUsers.java        # seeded test-account credentials, read from env vars only
├── core/
│   ├── BaseTest.java         # every test extends this: owns the Playwright/Browser/
│   │                         #   Context/Page lifecycle
│   ├── Browsers.java         # picks chromium/firefox/webkit
│   ├── FailureArtifacts.java # screenshot + trace capture, wired from BaseTest
│   └── Tags.java             # TestNG group-name constants (smoke/regression/auth/prod-smoke)
├── pages/                    # one class per screen — the Page Object Model
│   ├── BasePage.java
│   ├── DashboardPage.java    # home: search, category nav, product grid, "Load More"
│   ├── LoginPage.java / SignupPage.java
│   ├── ProductDetailPage.java
│   ├── WishlistPage.java
│   ├── SubmitDealPage.java / MySubmissionsPage.java
│   └── CookieConsentBanner.java
└── tests/                    # the actual test classes, one package per feature area
    ├── smoke/                # HomepageSmokeTest, ProdSmokeTest
    ├── browse/               # SearchAndBrowseTest
    ├── auth/                 # LoginTest, SignupValidationTest, RouteGuardTest
    ├── product/              # ProductDetailTest
    ├── wishlist/             # WishlistTest
    └── submitdeal/           # SubmitDealTest
testng.xml       # suite structure + parallelism (see "How parallelism works")
pom.xml          # dependencies, default groups, Surefire wiring
```

### The three layers, and why they're separate

1. **`config/`** — every knob (URL, browser, credentials) in one place, read
   from `-D` system properties or environment variables, never hardcoded.
   Change environments without touching a single test.
2. **`core/BaseTest`** — the plumbing every test needs (start a browser once
   per class, a clean context+page per test, save evidence on failure) lives
   here exactly once. A test class itself should never call
   `Playwright.create()`.
3. **`pages/`** — one class per screen, wrapping the raw Playwright
   `Locator`s for that screen behind readable method names
   (`loginPage.submitButton()`, not a bare CSS string repeated in five tests).
   If fomograb.com's markup changes, you fix it in **one** page object, not in
   every test that happens to touch that screen.
4. **`tests/`** — reads like a script a human would follow: "go to the
   homepage, open the first product, click wishlist, check `/wishlist`." All
   the DOM detail is hidden behind the page objects.

### How `BaseTest` manages the browser (read this before touching it)

- One `Playwright` + `Browser` per **test class**, created in `@BeforeClass`.
- One `BrowserContext` + `Page` per **test method**, created in `@BeforeMethod` —
  this is Playwright's recommended isolation unit (like a fresh private window),
  so tests never see another test's cookies/localStorage/session.
- `@AfterMethod` (`tearDown`) decides what happens to that context based on the
  method's own `ITestResult`: a passing test just closes it; a failing one
  first calls `FailureArtifacts.capture(...)` to save a screenshot and a
  [Playwright trace](https://trace.playwright.dev) before closing.
- This only works because `testng.xml` runs the suite with `parallel="classes"`,
  which is TestNG's guarantee that every method of one class runs on a single
  thread — required because a Playwright connection can only be driven from the
  thread that created it. Different classes can (and do) run concurrently.

## How elements are located

The app has **no `data-testid` attributes** anywhere (checked — there's a
single stray one in the app's own unit tests, nothing in production markup).
So locators are built, in priority order, from what real markup it does have:

1. `getByLabel(...)` for every form field — every input in Login, Signup, and
   SubmitDeal has a real `<label for>`.
2. `getByTitle(...)` for buttons whose visible text collides with something
   else on the same screen (e.g. Login's submit button vs. AuthPage's own
   "Login"/"Sign Up" tab buttons) — most buttons carry a unique `title` used
   as a tooltip, which doubles as a stable, unambiguous locator.
3. `getByRole(...)` with an accessible name, for things like the cookie banner
   ("Accept All") and the search-clear button (`aria-label="Clear search"`).
4. CSS class locators as a last resort, for elements with no semantic
   attributes at all (e.g. `.product-card`, `.vote-btn.up`, `.pd-tab`) — these
   are the app's own hand-written class names, not test-only hooks, so they're
   as stable as the app's CSS.

Several tests (`SearchAndBrowseTest`, `ProductDetailTest`, `WishlistTest`)
deliberately read whatever the **first real product card** on the homepage is,
rather than hardcoding a product name. That makes them pass against any real
catalog (dev, staging, or a prod mirror) instead of depending on a specific
seeded fixture — the tradeoff is they need at least one real product in the
database to run at all.

## Environments

| Group | Target | Notes |
|---|---|---|
| `smoke`, `regression`, `auth` | `-Dbase.url` (default `http://localhost:5173`) | Your local dev stack or a staging deploy. |
| `prod-smoke` | hardcoded `https://www.fomograb.com` inside `ProdSmokeTest` | Ignores `-Dbase.url` on purpose. |

**Never point the `auth`/`submitdeal` groups at production.** Two reasons,
both found in the app's own source:

- `src/config.ts` falls back to Cloudflare's universal *always-passes*
  Turnstile test key (`1x00000000000000000000AA`) whenever
  `VITE_TURNSTILE_SITE_KEY` isn't set at build time — true for local dev and
  most staging builds, which is exactly why login/signup are automatable
  there. Production sets a real site key, which will simply block a scripted
  browser.
- `server/routes/auth.js` rate-limits login/signup. Even if Turnstile weren't
  in the way, scripted attempts would just start 429-ing real users.

`prod-smoke` exists precisely so you still get a live health check of
production — homepage renders, `robots.txt`/`sitemap.xml` are served, a direct
product URL deep-links correctly — without touching anything that writes data
or fights the site's own bot defenses. It's excluded from the default
`./mvnw test` run (see `excludedGroups` in `pom.xml`) — opt in explicitly:

```bash
./mvnw test -Dgroups=prod-smoke -DexcludedGroups=
```

## Groups (TestNG's version of tags)

Defined once as constants in `core/Tags.java` so a typo is a compile error:

- **`smoke`** — fast, read-only chrome checks. Safe anywhere.
- **`regression`** — broader feature coverage (search, product detail,
  wishlist). Needs a backend with real product data.
- **`auth`** — login/signup/gated-route/submit-deal. Rate-limited and
  single-threaded on purpose (see `testng.xml`).
- **`prod-smoke`** — see "Environments" above.

## Reports

Every run produces:

- Surefire's own XML/text summary: `target/surefire-reports/`
- An [Allure](https://allurereport.org/) results directory:
  `target/allure-results/` — includes the failure screenshot/trace as
  attachments. View it with:

  ```bash
  # requires the Allure CLI: https://allurereport.org/docs/install/
  allure serve target/allure-results
  ```
- On any failing test: `target/test-results/<ClassName>_<methodName>/failure.png`
  and `trace.zip` (open the trace at https://trace.playwright.dev — drag the
  zip in, no upload needed).

## CI

`.github/workflows/ui-tests.yml` runs two independent jobs:

- **`ui-tests`** (push/PR/manual) — smoke+regression+auth against
  `vars.FG_BASE_URL`, which you need to set as a repo/environment variable
  pointing at a staging deployment (plus `secrets.FG_TEST_USER_EMAIL` /
  `FG_TEST_USER_PASSWORD`). Until that's configured it'll run against
  localhost inside the runner and fail — expected, see "Environments".
- **`prod-smoke`** (daily cron + manual) — works out of the box, no secrets
  needed, since it always targets the real production site.

## Extending the suite

Adding coverage for a new screen:

1. Add a page object under `pages/` — constructor takes the shared `Page`,
   methods return `Locator`s or perform an action. Look at the real component
   in the app's `src/components/` for label/title/class names before writing
   a selector; don't guess.
2. Add a test class under `tests/<feature>/`, extending `BaseTest`, tagged
   with one of the groups in `Tags.java` (or a new one, added there first).
3. If it's a new feature package, add it to the right `<test>` block in
   `testng.xml` (or a new one, if it needs its own parallelism rules like
   `auth` does).

## Known gaps (by design, not oversight)

- **No cart/checkout coverage** — the app doesn't have one; it's a deals
  aggregator, not a storefront (see its own `PROJECT-STRUCTURE.md`).
- **The primary "Buy" button is never actually clicked through** — it's a
  real affiliate link to a third-party marketplace (Amazon/Walmart/eBay/...).
  `ProductDetailTest` asserts on the `href`/`target` attributes instead of
  navigating there, to avoid firing live requests at affiliate partners on
  every CI run.
- **Admin dashboard has no coverage yet** — deliberately out of scope for a
  first pass given its blast radius (user management, product edits); would
  need its own tightly-scoped, read-mostly test class if added.
