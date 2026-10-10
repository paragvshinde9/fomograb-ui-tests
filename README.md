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

# Backend origin, for tests that mint a session over HTTP instead of driving the
# login form (see "Signing in without the login form"). Defaults to port 5000,
# which is the app's own local default — override if your backend differs.
export FG_API_URL="http://localhost:5000"
```

Seed that account once with the app's own script (`node server/seedTestUser.js`
in the fomograb repo) — the suite never creates accounts through the UI itself,
because the backend rate-limits signup/login and a real signup also
fires a verification email.

## Project layout

```
src/test/java/com/fomograb/uitests/
├── config/
│   ├── TestConfig.java       # base.url / api.url / browser / headless / timeouts — all overridable
│   └── TestUsers.java        # seeded test-account credentials, read from env vars only
├── core/
│   ├── BaseTest.java         # every test extends this: owns the Playwright/Browser/
│   │                         #   Context/Page lifecycle
│   ├── Browsers.java         # picks chromium/firefox/webkit
│   ├── FailureArtifacts.java # screenshot + trace capture, wired from BaseTest
│   ├── Preflight.java     # one HTTP probe that the app is actually up, before
│   │                      #   any browser starts — fails the run once, not 30 times
│   ├── RetryAnalyzer.java    # retries a failed test once (flake absorption, not bug-hiding)
│   ├── RetryTransformer.java # auto-applies RetryAnalyzer to every @Test — registered in testng.xml
│   ├── Tags.java             # TestNG group-name constants (smoke/regression/auth/prod-smoke/e2e)
│   └── api/
│       └── ApiSessions.java  # mints a signed-in session over HTTP, for tests that need to
│                             #   *be* logged in rather than to test logging in
├── pages/                    # one class per screen — the Page Object Model
│   ├── BasePage.java
│   ├── DashboardPage.java    # home: search, category nav, product grid, "Load More", session/logout
│   ├── LoginPage.java / SignupPage.java
│   ├── ProductDetailPage.java
│   ├── WishlistPage.java
│   ├── SubmitDealPage.java / MySubmissionsPage.java
│   └── CookieConsentBanner.java
└── tests/                    # the actual test classes, one package per feature area
    ├── smoke/                # HomepageSmokeTest, CookieConsentTest, ProdSmokeTest
    ├── browse/               # SearchAndBrowseTest
    ├── auth/                 # LoginTest, SignupValidationTest, RouteGuardTest,
    │                         #   LoginSubmitDealLogoutE2ETest, SignupToVerificationE2ETest (disabled)
    ├── product/              # ProductDetailTest
    ├── wishlist/             # WishlistTest
    ├── submitdeal/           # SubmitDealTest
    └── e2e/                  # BrowseToWishlistE2ETest — multi-page guest journeys
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

- One `Playwright` + `Browser` per **test class**, created in `@BeforeClass`, held
  in **instance** fields.
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

**Two rules that follow from that last point, both learned the hard way:**

1. **Never make the `Playwright`/`Browser` fields `static`.** They were, and it
   was a race: with `thread-count="3"` three classes write the same two fields
   in `@BeforeClass`, last writer wins, and the others end up driving a browser
   created on a different thread while their own Playwright processes leak — and
   the first `@AfterClass` to finish closes a browser two other classes are
   still using. TestNG instantiates each test class separately, so plain
   instance fields are already scoped correctly per class *and* per thread.
2. **Never put `timeOut` on a `@Test` in this suite.** TestNG implements method
   timeouts by running the body on a *separate thread* from `@BeforeMethod` —
   so `page` and `context` get handed to a thread that didn't create them. Use
   Playwright's own timeouts instead (`navigation.timeout`, per-call
   `setTimeout(...)`), which also fail better: a real Playwright error with a
   saved trace, instead of a bare TestNG timeout with no artifacts.
   `ProdSmokeTest` shows the pattern.

Both of these were silent — the suite "passed" while `RetryAnalyzer` absorbed
the resulting flake. That is the argument for making flake *visible* rather than
simply retried.

### Cookie consent is pre-accepted, not clicked

Every context gets an init script that writes the app's `fg_cookie_consent` key
before any page script runs, so the banner never mounts. `goTo()` therefore just
navigates.

It used to wait up to 2.5s for the banner and click "Accept All" on every
navigation — which was both slow and a race, because once the choice is stored
the banner never appears again, so every *later* navigation in the same test
burned the full timeout waiting for an element that would never show up.

The one test whose subject *is* the banner overrides
`preSeedCookieConsent()` to `false`. Since that hook is per-class, it lives in
its own class (`CookieConsentTest`) rather than inside `HomepageSmokeTest`.

### Signing in without the login form

Tests that need to **be** signed in (currently `SubmitDealTest`) override
`storageState()` and get a session minted over HTTP by `core/api/ApiSessions`,
seeded into the context before the first navigation — no login form, no
Turnstile widget. Tests whose subject *is* logging in (`LoginTest`, and the
login→logout journey in `LoginSubmitDealLogoutE2ETest`) still drive the real UI,
because that is the thing they are testing.

This works because of how the app carries a session: the short-lived access
token is kept **in memory only**, and the session is restored on page load from
an `httpOnly` cookie — so seeding that cookie is enough. The cookie belongs to
the **API** origin, which is why `FG_API_URL` exists separately from
`FG_BASE_URL`. Point them at a matching pair, or the cookie is minted for the
wrong host and the session silently won't apply — which looks like a bad test
account rather than a config mistake.

⚠️ **Do not "optimise" this by caching the session to a file.** That is the usual
advice and it does not work here: session tokens are single-use, so a shared
cache file holds a token the first context has already consumed, and later
contexts fail as intermittent auth flake rather than a clean error.
`ApiSessions` therefore mints a fresh session per test method — a few hundred
milliseconds, and the whole class of problem goes away.

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

### ⚠️ The homepage has two layouts, and `.product-card` is the rarer one

`Dashboard.tsx` renders one of two things depending on `isBrowseMode`:

| Mode | When | Card class | Name element |
|---|---|---|---|
| **Homepage** | a bare `/`, no search/filter | `.section-product-card` | `p.section-card-name` |
| **Browse** | once a search, category, filter or sort is applied | `.product-card` | `h5` |

So on a bare `/`, the flat `.product-card` grid **never renders at all** — the
page is a set of curated sections ("Smartphones", "Laptops", …). The sectioned
homepage landed in the app on 2026-06-10.

`DashboardPage.productCards()` still matches only `.product-card`, which means
`HomepageSmokeTest`, `SearchAndBrowseTest` and `BrowseToWishlistE2ETest` all
assume browse-mode markup on a page that serves homepage-mode markup. They pass
today only against a backend whose curated sections come back empty; against
production-like data they fail with "element(s) not found". `ProdSmokeTest` hit
exactly this and now matches either layout — the rest still need fixing, in the
one place they share (`DashboardPage:81`).

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
| `auth` also reads | `-Dapi.url` (default `http://localhost:5000`) | Backend origin, for the HTTP-minted session. Must match the API the frontend is built against. |
| `prod-smoke` | hardcoded `https://www.fomograb.com` inside `ProdSmokeTest` | Ignores `-Dbase.url` on purpose. |

If nothing is listening at the configured `base.url`, the run stops in
`@BeforeClass` with one message naming the URL and how to change it — it does
not let 30 tests each rediscover the same connection error (see
`core/Preflight`). That is a hard failure, not a skip: a missing *optional*
test account skips the tests that need it, but a missing *app* means nothing was
tested at all, which must never be able to report green. `ProdSmokeTest` opts
out (it hardcodes its own targets).

**Never point the `auth`/`submitdeal` groups at production.** Two reasons:

- **Bot protection.** Environments meant to be automated (local dev, and
  staging builds configured for testing) accept a placeholder bot-check token,
  which is exactly why login/signup are scriptable there. Production runs real
  bot protection and will simply block a scripted browser. Minting the session
  over HTTP (`ApiSessions`) doesn't change that — the token is validated
  server-side too.
- **Rate limiting.** Login and signup are rate-limited. Even without the bot
  check in the way, scripted attempts against production would just start
  429-ing real users.

`prod-smoke` exists precisely so you still get a live health check of
production without touching anything that writes data or fights the site's own
bot defenses. It has 17 read-only checks in four areas:

- **Site and hosting** — homepage title, `robots.txt`, `sitemap.xml`, security
  headers (HSTS/CSP/nosniff/frame options), `http://` redirecting to `https://`,
  and a product URL deep-linking on a cold load (which is what actually
  exercises the SPA hosting rewrite).
- **Backend API** — `/api/health` reports healthy with the database connected,
  the products API serves a non-empty catalog, an unknown product answers 404,
  and loading the homepage triggers no 5xx API calls.
- **Homepage behaviour** — product cards render, search returns results (the
  query is taken from the live catalog), the category menu opens and closes,
  and the theme toggles.
- **Other public pages** — the login form renders (never submitted), a new
  visitor's wishlist shows its empty state, and a product page has a valid
  outbound Buy link (never clicked).

It also carries `sitemapProductUrlsResolve`, currently `@Test(enabled = false)`:
it samples the sitemap and checks the URLs resolve, which is worth guarding
because a page listing no product still returns HTTP 200 and "loads" fine, so
nothing else here would notice. See its Javadoc for why it is parked rather than
deleted.

The group is excluded from the default `./mvnw test` run (see `excludedGroups`
in `pom.xml`) — opt in explicitly:

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
- **`e2e`** — multi-page journeys, always double-tagged with `regression` or
  `auth` so they run under the normal groups too; use `-Dgroups=e2e` to run
  just the journeys on their own.

## E2E tests vs. everything else

Most of this suite tests one feature in isolation (`ProductDetailTest` only
cares about the product page, `WishlistTest` only cares about `/wishlist`).
The `e2e` group instead stitches several pages into one continuous session —
closer to how a real visitor actually moves through the site:

- **`BrowseToWishlistE2ETest`** (guest, no login) — search for a real deal,
  read its Prices/History tabs, wishlist it, find it on `/wishlist`, follow
  "View Deal" back to the product, then remove it.
- **`LoginSubmitDealLogoutE2ETest`** (needs a seeded account) — get gated to
  `/login`, log in, submit a deal, confirm it's pending in My Submissions, log
  out, and confirm the gate closes again — proving logout actually cleared the
  session, not just the header.
- **`SignupToVerificationE2ETest`** — present but `@Test(enabled = false)`;
  see its Javadoc for exactly why (it would need to fire a real, un-cleanable
  signup on every run to prove one screen transition) and what unlocks it.

Each is written as **one long `@Test` method**, not several small ones —
`BaseTest` gives every `@Test` method a fresh `BrowserContext`, so splitting a
journey across methods would just lose the session between "steps."

## Other TestNG features in play (and why)

Beyond the basics, a few of TestNG's other annotations/attributes show up
where they earn their place — not sprinkled in for their own sake:

- **`RetryAnalyzer` + `RetryTransformer`** (`core/`) — every `@Test` gets one
  automatic retry on failure, to absorb genuine UI flake (an animation/network
  race) without masking real bugs. Capped at 1 retry on purpose — see the
  Javadoc on `RetryAnalyzer` for the tradeoff with rate-limited auth tests.
- **`@DataProvider`** (`SignupValidationTest.shortPasswords`) — sweeps several
  password lengths under the 8-char minimum through one test method instead of
  one near-identical method per length.
- **`SoftAssert`** (`HomepageSmokeTest.footerQuickLinksAreAllPresent`) —
  checks six independent footer links and reports every missing one in a
  single run, instead of stopping at the first and playing whack-a-mole.
- **`invocationCount`** — deliberately **not** used, and worth knowing why.
  `SearchAndBrowseTest.categoryMenuOpensAndClosesReliably` used it to repeat a
  flaky interaction 3 times, but TestNG shares **one** `IRetryAnalyzer` instance
  across all invocations of a method: the first failure spent the suite's single
  retry and invocations 2 and 3 ran with no flake protection — the opposite of
  that test's purpose. Nothing on `ITestResult` distinguishes "next invocation"
  from "retry of the previous one", so the repetition moved into a loop inside
  the method, where it is one test with one working retry budget.
  `@DataProvider` is unaffected: each row genuinely gets its own analyzer
  instance, which is why the parameterised sweep below still works as expected.
- **`timeOut`** — deliberately **not** used either; see rule 2 under "How
  `BaseTest` manages the browser". `ProdSmokeTest` needs a fail-fast budget (it
  is the one class talking to a real third-party-fronted host), and gets it from
  Playwright's own navigation/request timeouts instead.
- **`priority`** (`RouteGuardTest`) — purely for report readability (the tests
  aren't order-dependent); the three "redirected" cases read as the rule and
  the public-wishlist counterexample reads more clearly reported last.

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

`.github/workflows/ui-tests.yml` runs three independent jobs:

- **`build`** (always) — `./mvnw test-compile`. No app, no secrets; it's the one
  thing this repo can always check on its own, and it's what catches a PR that
  breaks compilation while the suite job below is skipped. It also emits a
  workflow warning when `FG_BASE_URL` isn't configured, so a skipped suite is
  visible rather than quietly absent.
- **`ui-tests`** (push/PR/manual) — smoke+regression+auth against
  `vars.FG_BASE_URL`, which you need to set as a repo/environment variable
  pointing at a staging deployment (plus `secrets.FG_TEST_USER_EMAIL` /
  `FG_TEST_USER_PASSWORD`). **The job is skipped unless `FG_BASE_URL` is set**,
  because a browser suite with no app to point at has nothing to say. It used to
  run anyway, against a `localhost:5173` that doesn't exist inside a runner, and
  every push produced the same ~30 `ERR_CONNECTION_REFUSED` failures — a red
  check that means "not configured" is worse than no check, because people learn
  to scroll past it.
- **`prod-smoke`** (daily cron + manual) — works out of the box, no secrets
  needed, since it always targets the real production site. This is the browser
  coverage you get for free; it is deliberately *not* wired to `pull_request`,
  so PR status never depends on production (or a cold Render API) being up.

Pointing it at a staging deployment is the whole setup:

```
Settings > Secrets and variables > Actions > Variables
  FG_BASE_URL   https://staging.fomograb.com      # frontend
  FG_API_URL    https://staging-api.fomograb.com  # the API that frontend is built against
Settings > Secrets and variables > Actions > Secrets
  FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD      # seeded non-admin account
```

Without `FG_API_URL` and the two secrets the job still runs — the `auth` tests
just skip themselves (see "Quick start").

### ⚠️ Don't put defaults in `pom.xml`'s `<properties>`

`base.url`, `browser` and `headless` are declared there **empty on purpose**, and
`FG_BASE_URL` only works because they are. Surefire forwards each of them into
the forked test JVM as a system property, and `TestConfig` reads a system
property *before* the matching env var — so a default written in the pom is
passed on every run and silently outranks the environment. That was a real bug:
`pom.xml` carried `<base.url>http://localhost:5173</base.url>`, so CI tested
localhost no matter what `FG_BASE_URL` said, and setting the repo variable would
not have fixed the failure it appeared to explain.

Precedence, in order: `-Dbase.url=...` on the command line → `FG_BASE_URL` → the
fallback in `TestConfig`. Defaults belong in `TestConfig` and nowhere else.

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
