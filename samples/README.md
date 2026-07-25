# AEGIS Samples

Three complete, standalone examples of embedding AEGIS via the public SDK (`aegis-api`) — no knowledge of `aegis-core`'s internals required. Each is its own minimal Maven module depending only on `aegis-api`.

| Sample | Site | Flow | Notable config |
|---|---|---|---|
| `sample-saucedemo` | saucedemo.com | Login | Default `greedy` strategy, headed chromium |
| `sample-orangehrm` | opensource-demo.orangehrmlive.com | Login | `adaptive` strategy |
| `sample-nopcommerce` | demowebshop.tricentis.com | Registration | `form-first` strategy — see note below |

## Run one in under 15 minutes

1. From the repo root: `mvn -pl samples/sample-saucedemo exec:java` (each sample's `pom.xml` already has `exec-maven-plugin` preconfigured with its `Main` class — no extra flags needed).
2. Watch a real browser window open and log into saucedemo.com on its own.
3. Open the generated `samples/sample-saucedemo/reports/aegis-report-*.html` in a browser.

That's the whole loop. To point AEGIS at your own application instead:

1. Copy one of these 3 module directories (pick whichever flow is closest to yours — login or registration) and rename it.
2. Edit `application.yml` — at minimum, set `application.baseUrl`. Add credentials/success-condition as needed.
3. Rename the `XApplication` class's `name()` to your app's name, and update `Main`'s import/package to match.
4. Run it the same way.

No AEGIS source code needs to change for this. See `USAGE.md`'s "SDK Quick Start" section for the full config reference (every `application.yml` key, every `mission.strategy` option).

## Why nopCommerce points at demowebshop.tricentis.com

`demo.nopcommerce.com` — the site actually named "nopCommerce" — has bot protection that blocked every request from the environment this sample was built and verified in, including real Playwright traffic, not just a bare HTTP client. `demowebshop.tricentis.com` runs the same underlying nopCommerce platform (Tricentis hosts it specifically for test-automation training) and is reachable and well-behaved, so the sample points there instead. If `demo.nopcommerce.com` is reachable from your own network, only `application.baseUrl` needs to change to point at it directly.

## Why the strategies differ per sample

Not arbitrary — each was picked after actually running the mission and watching what happened, a genuine demonstration of why `mission.strategy` is configurable at all:

- **SauceDemo** — simple page, small element count, `greedy` (the default) reaches the goal directly.
- **OrangeHRM** — `greedy` alone fixated on the password field (it scores higher than username) and never got there within a normal iteration budget. `adaptive` breaks out of exactly that kind of stall by falling back to coverage-aware exploration once a few iterations pass with nothing new discovered.
- **nopCommerce/DemoWebShop** — a registration page with 60+ other links on it; `greedy` wandered into them instead of finishing the form. `form-first` biases toward completing forms over navigating away.

See `USAGE.md` §5 for the full strategy list.
