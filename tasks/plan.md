# LPD-89681 — Implementation Plan

Companion to [SPEC.md](../SPEC.md). Breaks the spec into ordered, verifiable tasks, sliced so that each task is a complete deployable change (working code, tests, and a commit), not a horizontal layer.

## Constraints That Shape the Plan

- **Two deployables, but only one is portal-side.** The portal-side delivery is a single site-initializer schema delta (T1). All behavior — concurrency guard, parser, aggregator, prune — lives in the new Spring Boot client extension under `workspaces/liferay-seostudio-workspace/**`. Per SPEC §2 there is no `seo-studio-web` Java change in this ticket.
- **Generated code is sacred.** Object-definition JSON changes flow through the site initializer; any REST changes (none in this ticket) flow through `gw buildREST` into separate commits.
- **Integration-test mandate.** Never start an integration test run without confirming a deployable server exists. The plan calls out which tasks need user gating before integration runs.
- **Commit hygiene.** One `LPD-89681` ticket prefix per commit, sentence-case title, group by module, generated files in their own commits.

## Dependency Graph

```
   ┌─────────────────────────────────┐    ┌──────────────────────┐
   │ T1  Scan-name list + name field │    │ T2  CX workspace +   │
   │     (site-initializer)          │    │     Spring Boot skel │
   └─────────────────────────────────┘    └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T3  LogParser SPI + │
                                          │     Apache parser   │
                                          └──────────┬──────────┘
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T4  Aggregator      │
                                          │     (+ bot match)   │
                                          └──────────┬──────────┘
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T5  PortalClient    │
                                          │     (HTTP layer)    │
                                          └──────────┬──────────┘
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T6  Controller —    │
                                          │     happy path,     │
                                          │     pre-check,      │
                                          │     post-complete   │
                                          │     prune           │
                                          └──────────┬──────────┘
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T7  Controller —    │
                                          │     error paths     │
                                          │     (400 / 409)     │
                                          └──────────┬──────────┘
                                                     ▼
                                          ┌─────────────────────┐
                                          │ T8  End-to-end      │
                                          │     smoke test      │
                                          └─────────────────────┘
```

Key edges:

- T1 and T2 are independent and can ship in either order; the CX needs T1 deployed only at end-to-end test time (T8).
- T3, T4, T5 are CX-internal, mockable, and can be done in any order; T6 composes them.
- T6 owns three behaviors: the happy path, the pre-check (AC3), and the post-complete prune (AC6). The pre-check uses `ScanClient` from T5.
- T7 depends on T6 (same controller).
- T8 needs T1 deployed **and** the CX through T7.

## Slicing Rationale

Each task is a vertical slice — a complete change that builds, tests, and commits — not a layer ("all interfaces", "all impls"). The CX's internal modules (T3–T5) look horizontal but are kept narrow on purpose: each ships with its own unit tests and the controller composes them in T6. That way, every commit on this branch passes `gw test` on its own.

## Phases & Checkpoints

### Phase A — Portal Foundation (T1)

Adds the schema delta the client extension will rely on. Independently deployable; useful even without the CX (an operator could create scans by hand via the object headless API).

**Checkpoint A** before moving on:
- Site initializer rebuild succeeds and a fresh bundle exposes the new `name` Picklist on `SEOStudioScan`.
- An object headless `POST /o/c/seostudioscans` with `name = "aiRequestProcessor"` succeeds; `name = "foo"` fails picklist validation.

### Phase B — Client Extension Skeleton (T2)

Bootstraps the workspace and a runnable Spring Boot app with a `/ready` probe and both OAuth client-extension registrations. No business logic.

**Checkpoint B**:
- `./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootJar` succeeds.
- `./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootRun` boots and `GET /ready` returns 200.
- `client-extension.yaml` registers both `*-oahs` and `*-oaua` per SPEC §4.1.

### Phase C — Client Extension Pipeline (T3–T7)

Composes the CX's internal pieces behind `POST /process`. Each task in C lands its own unit tests; the controller in T6 wires them together with the portal HTTP client, with the pre-check (AC3) and the post-complete prune (AC6) folded into T6/T7.

**Checkpoint C**:
- Unit tests for parser, bot matcher, aggregator, portal client all green.
- `AIRequestProcessorRestControllerTest` proves AC1, AC3 (pre-check returns 409 when a queued/running scan exists), AC4, AC5, AC6 (the post-complete DELETE loop runs against the right cutoff), and AC7. AC2 is covered by T7's 400 path.

### Phase D — Verification (T8)

End-to-end smoke against a local server. **Run only after the user confirms a testable server is started.**

**Final readiness gate**:
- A real Apache Combined Log Format file uploaded to `POST /process` produces `SEOStudioAIRequest` rows for the expected (bot, page, day) tuples and a completed `SEOStudioScan` with `name = "aiRequestProcessor"`.
- Re-uploading the same file is a no-op (new scan, zero new rows beyond pruning).
- A seeded row at `requestDate = today − 400d` is gone after a scan completes (CX-side prune).
- A concurrent second `POST /process` while a scan is queued/running returns HTTP 409 (CX pre-check).
- `/format-source` clean across the whole branch.

## Task Details

### T1 — Add `scan name` list type and `name` field on `SEOStudioScan`

- **Files**
  - NEW `modules/dxp/apps/seo-studio/seo-studio-site-initializer/src/main/resources/site-initializer/list-type-definitions/seo-studio-scan-name-list-type-definition.json` — `externalReferenceCode: "L_SEO_STUDIO_SCAN_NAMES"`, one entry `{key: "aiRequestProcessor", name: "AIRequestProcessor"}`.
  - MODIFY `modules/dxp/apps/seo-studio/seo-studio-site-initializer/src/main/resources/site-initializer/object-definitions/03-seo-studio-scan-object-definition.json` — append a `name` field, `businessType: "Picklist"`, `listTypeDefinitionExternalReferenceCode: "L_SEO_STUDIO_SCAN_NAMES"`, `indexed: true`, `required: true`, `system: true`. (No `indexedAsKeyword` and no `indexedLanguageId` — deliberately leaner than the sibling Picklist fields on this object; see note below.)
- **Depends on**: nothing.
- **Acceptance**
  - Site initializer reseeds without error.
  - `GET /o/c/seostudioscans/scopes/{companyId}` exposes the new field in the schema.
  - `POST /o/c/seostudioscans` with `name = "aiRequestProcessor"` succeeds; the same call with `name = "foo"` fails with a list-value validation error.
- **Verify**
  - `cd modules/dxp/apps/seo-studio/seo-studio-site-initializer && gw deploy -a`
  - Restart the bundle, then `curl -u test@liferay.com:test 'http://localhost:8080/o/c/seostudioscans/openapi.json' | jq .components.schemas.Scan.properties.name`
- **Commits**
  - `LPD-89681 Add SEO Studio Scan name picklist`

### T2 — Client extension workspace + Spring Boot skeleton

- **Files**
  - NEW `workspaces/liferay-seostudio-workspace/` — mirror the sibling `liferay-seostudio-pagespeed` workspace from [LPD-88837](https://liferay.atlassian.net/browse/LPD-88837) / [PR #3286](https://github.com/liferay-site-management/liferay-portal/pull/3286): `settings.gradle` (applies `com.liferay.workspace` plugin and includes the `poshi` subproject), `gradle.properties`, `package.json`, `gradle/wrapper/`, `gradlew{,.bat}`. No workspace-root `build.gradle`, no `configs/`, no `scripts/`.
  - NEW `workspaces/liferay-seostudio-workspace/client-extensions/liferay-seostudio-airequestprocessor/` — `build.gradle` (Spring Boot 2.7.18, JDK 21; applies `com.liferay.source.formatter`, `java-library`, `org.springframework.boot`; pulls `com.liferay.client.extension.util.spring.boot3`, `com.liferay.petra.string`, `com.liferay.portal.kernel`, `org.apache.commons.logging`, `org.json:json`, Spring Boot `spring-boot-starter-web` + `spring-boot-starter-oauth2-resource-server`), `Dockerfile` (`FROM liferay/jar-runner:latest`), `LCP.json` (port 58081 with `/ready` liveness/readiness probes), `client-extension.yaml` registering both `*-oahs` (`oAuthApplicationHeadlessServer`, single scope `Liferay.Object.Admin.REST.everything`) and `*-oaua` (`oAuthApplicationUserAgent`) per SPEC §4.1, `src/main/resources/application.properties` (imports the default + the `LIFERAY_ROUTES_*` configtrees), `src/main/resources/application-default.properties` (LXC config, `liferay-seostudio-airequestprocessor-oahs.oauth2.headless.server.client.secret=${LIFERAY_SEOSTUDIO_AIREQUESTPROCESSOR_OAHS_CLIENT_SECRET:}`, `liferay.oauth.application.external.reference.codes=` listing both ERCs, `liferay.oauth.urls.excludes=/ready`, `server.port=58081`).
  - NEW `SeoStudioSpringBootApplication.java` (annotated `@Import(ClientExtensionUtilSpringBootComponentScan.class)` + `@SpringBootApplication`) + `ReadyRestController.java` (returns `"READY"` on `GET /ready`, mirrors the `ReadyRestController` in `liferay-seostudio-pagespeed`).
- **Depends on**: nothing.
- **Acceptance** — Checkpoint B above.
  - `bootJar` produces a runnable JAR.
  - `bootRun` starts; `curl -fsS http://localhost:58081/ready` returns 200.
- **Verify**
  - `./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootJar`
  - `./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootRun &` then `curl -fsS http://localhost:58081/ready`
- **Commits**
  - `LPD-89681 Add SEO Studio client extension workspace`
  - `LPD-89681 Add SEO Studio Spring Boot client extension skeleton`

### T3 — `LogParser` SPI + `ApacheCombinedLogParser`

- **Files**
  - NEW `LogParser.java` (interface) — `String formatKey()`, `Stream<JSONObject> parse(InputStream, Instant lowerBound)`. Each `JSONObject` carries the keys `userAgent`, `requestPath`, `requestTime` (ISO-8601 UTC string), and `rawLine`. Using `org.json.JSONObject` rather than a `LogEntry` record matches the codebase's JSON-heavy style. The `lowerBound` parameter is honored by `ApacheCombinedLogParser`, which reads lines newest-first (assuming oldest-first sort) and short-circuits the first time a cheap timestamp pre-extract is below the bound, so re-uploading the same file is a no-op after one byte-level check.
  - NEW `InvalidLogFormatException.java` extends `IOException`; carries the offending line number + line text.
  - NEW `ApacheCombinedLogParser.java` — `@Component` with `formatKey() = "apache-combined"`. Streaming parser (`BufferedReader.lines()`), drops blank lines, rejects lines that don't match the Combined regex with `InvalidLogFormatException`.
  - NEW `ApacheCombinedLogParserTest.java` — golden lines, malformed lines, IPv6, non-ASCII paths, missing user-agent, empty file.
  - NEW test fixtures `src/test/resources/logs/apache-combined-valid.log`, `apache-combined-malformed.log`.
- **Depends on**: T2 (module exists).
- **Acceptance** — covers AC7 partially (extension point exists).
  - Valid log returns the expected count of `JSONObject` entries with `requestTime` parsed to ISO-8601 UTC strings.
  - First malformed line throws `InvalidLogFormatException` with `lineNumber`.
- **Verify**
  - `cd workspaces/liferay-seostudio-workspace && ./gradlew :client-extensions:liferay-seostudio-airequestprocessor:test --tests ApacheCombinedLogParserTest`
- **Commits**
  - `LPD-89681 Add log parser SPI and Apache Combined parser`

### T4 — `AIRequestAggregator` + `AggregationKey`

- **Files**
  - NEW `AggregationKey.java` (record): `agentName`, `pageURL`, `requestDate` (`LocalDate` in UTC).
  - NEW `AIRequestAggregator.java` — `accept(JSONObject parsedLog, Collection<String> enabledAgentNames)` (the JSONObject is the parser output from T3). Internally substring-matches the entry's `userAgent` against the enabled-agent list (case-sensitive, first match in iteration order wins); entries without a match are dropped. On `build()` returns `Map<AggregationKey, Integer>`. Strips query string and host from the request path.
  - NEW `AIRequestAggregatorTest.java` — sums duplicates, UTC day boundary at 00:00, query string stripping, two-line same-key, empty input, bot-match scenarios (no match dropped, first match wins, case sensitivity), missing-userAgent dropped.
- **Depends on**: T3 (parsed log `JSONObject` shape).
- **Acceptance** — covers AC4 (bot filtering) and AC5 (aggregation).
  - `(agentName, pageURL, requestDate)` collisions are summed.
  - Trailing `?foo=bar` and absolute URLs both reduce to `/path`.
  - Entries whose `userAgent` does not match any enabled agent name are excluded.
- **Verify**
  - `gw test --tests AIRequestAggregatorTest`
- **Commits**
  - `LPD-89681 Add AI request aggregator`

### T5 — `PortalClient` (HTTP layer to the headless API)

- **Files**
  - NEW `PortalClient.java` — wraps the `RestTemplate`/`WebClient` with auth interceptor.
  - NEW `ScanClient.java` — `findLastCompletedAIRequestProcessorScan(domainId)`, `findInFlightAIRequestProcessorScan(domainId)` (returns the first `name = aiRequestProcessor` scan in `state in (queued, running)` for the domain, or empty), `createScan(domainId, name, scanType, ...)`, `updateScanState(scanId, state, errorMessage?)`.
  - NEW `AIBotConfigurationClient.java` — `listEnabledAgentNames(domainId)`.
  - NEW `AIRequestClient.java` — `batchCreate(scanId, domainId, Map<AggregationKey, Integer>)` plus `findStaleRequestIds(domainId, cutoffDate)` and `deleteById(id)` for the post-complete prune. Bounded batch size (e.g., 100/request).
  - NEW unit tests using Spring `MockRestServiceServer`: happy responses + 4xx/5xx → typed exceptions.
- **Depends on**: T2 (Spring context).
- **Acceptance**
  - Each client method serializes the documented payload, accepts the documented response, and surfaces remote errors as typed exceptions the controller can convert to HTTP status codes.
- **Verify**
  - `gw test --tests "*Client*Test"`
- **Commits**
  - `LPD-89681 Add headless API clients for AI request processor`

### T6 — `AIRequestProcessorRestController` — happy path

- **Files**
  - NEW `AIRequestProcessorRestController.java` — `@PostMapping("/process")` taking `@RequestParam("domainId")`, `@RequestParam(value="format", defaultValue="apache-combined")`, `@RequestPart("file") MultipartFile`.
  - Orchestration order:
    1. **Pre-check (AC3).** `ScanClient.findInFlightAIRequestProcessorScan(domainId)` — internally `GET /o/c/seostudioscans?filter=name eq 'aiRequestProcessor' and r_seoStudioDomainToSEOStudioScans_seoStudioDomainId eq <domainId> and state in ('queued','running')&pageSize=1`. If a result comes back, throw `ConcurrentScanException` → 409 (handler added in T7). Scans on the same domain with a different `name` (e.g., a future `sitemapProcessor`) are deliberately ignored — only same-name overlaps block the upload.
    2. Pick `LogParser` by `formatKey`.
    3. Compute `lowerBound` per SPEC §2.3.1 via `ScanClient.findLastCompletedAIRequestProcessorScan(domainId)`.
    4. `AIBotConfigurationClient.listEnabledAgentNames(domainId)`.
    5. `ScanClient.createScan(... state=running)`.
    6. Call `parser.parse(file.getInputStream(), lowerBound)` — propagates `InvalidLogFormatException` → 400 (handler added in T7); the parser itself does the `lowerBound` short-circuit. Stream `.filter(botMatcher)` + `.forEach(aggregator::accept)`.
    7. `AIRequestClient.batchCreate`.
    8. `ScanClient.updateScanState(... state=completed)`.
    9. **Post-complete prune (AC6).** `AIRequestClient.findStaleRequestIds(domainId, scan.requestDate.minusDays(365))` then `deleteById` per result. Logged at `WARN` on failure; does not flip the scan back to `failed`.
    10. Return `{scanId}`.
  - On any unchecked exception between steps 5 and 8: `ScanClient.updateScanState(... state=failed, errorMessage=ex.getMessage())` then rethrow.
  - NEW `AIRequestProcessorRestControllerTest.java` — happy path with mocked `PortalClient`; asserts pre-check called before parse (AC3), bot filtering (AC4), aggregation (AC5), prune call shape and cutoff date (AC6), parser dispatch (AC7).
- **Depends on**: T3–T5.
- **Acceptance** — covers AC1, AC3, AC4, AC5, AC6, AC7.
- **Verify**
  - `gw test --tests AIRequestProcessorRestControllerTest`
- **Commits**
  - `LPD-89681 Add AI request processor controller`

### T7 — `AIRequestProcessorRestController` — error paths

- **Files**
  - EXTEND `AIRequestProcessorRestController` — `@ExceptionHandler(InvalidLogFormatException.class)` returns 400 with the offending line number; `@ExceptionHandler(ConcurrentScanException.class)` returns 409 (raised by the T6 pre-check when an in-flight `aiRequestProcessor` scan exists for the domain).
  - NEW `ConcurrentScanException.java` in the CX (unchecked) — carries the conflicting scan's id for the response body.
  - EXTEND `AIRequestProcessorRestControllerTest` — 400 on `apache-combined-malformed.log`; 409 when the mocked `ScanClient.findInFlightAIRequestProcessorScan` returns a scan; **200** (no 409) when the mocked client returns an in-flight scan with a different `name` for the same domain — proves the pre-check only blocks on same-name overlap, not on any-scan overlap.
- **Depends on**: T6.
- **Acceptance** — covers AC2 and tightens the AC3 surface (CX maps the pre-check signal to 409).
- **Verify**
  - `gw test --tests AIRequestProcessorRestControllerTest`
- **Commits**
  - `LPD-89681 Handle invalid log and conflict in AI request processor`

### T8 — End-to-end smoke

- **Steps** (manual, gated on the user confirming a server)
  1. Deploy: `cd modules/dxp/apps/seo-studio/seo-studio-site-initializer && gw deploy -a`; bounce Tomcat.
  2. Build CX: `cd workspaces/liferay-seostudio-workspace && ./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootJar`.
  3. Start CX: `./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootRun &`.
  4. Create a domain via the portal UI (or POST `/o/c/seostudiodomains`).
  5. `curl -F file=@apache-combined-valid.log -F domainId=$DOMAIN -F format=apache-combined http://localhost:58081/process` → expect `{scanId:N}`.
  6. `GET /o/c/seostudioairequests?filter=...` → expect the aggregated rows.
  7. Repeat step 5 with the same file → expect 0 new rows, new scan with the same `requestDate` window.
  8. **AC6, CX-side prune.** Seed a row with `requestDate = today − 400d` for the domain via direct `POST /o/c/seostudioairequests`, then `POST /process` again, confirm the row is gone after the request returns.
  9. **AC3, CX-side concurrency.** Manually `POST /o/c/seostudioscans` to create a `name=aiRequestProcessor` scan in `state=running` for the domain, then `POST /process`; expect HTTP 409.
- **Acceptance** — every step passes; the existing `AIRequestResource.getDomainAIRequestsPage` returns the expected aggregated view.
- **Verify** — interactive.
- **Commits** — none (verification only).

## Final-Step Discipline

Before any push for review:

- `ant format-source-current-branch` from `portal-impl`.
- `git log --oneline master..HEAD` — every commit titled `LPD-89681 <sentence-case>`, no body needed.
- No commit mixes generated and hand-written code.

## Risks and Open Questions

- **Multipart size.** Apache logs can be huge. Spring Boot's default multipart cap is 1 MB. Need to raise `spring.servlet.multipart.max-file-size` and `max-request-size` in `application.properties` (e.g., 512 MB), and confirm the CX heap is sized appropriately. Track as part of T6 (the controller that actually owns the upload surface), not T2 — the skeleton in T2 has no `/process` endpoint yet.
- **Auth from UI to CX.** The UI is out of scope, but T7's 409 path depends on the CX passing through a JWT to the portal. The OAuth user-agent in T2 covers this for the back call into the portal; the inbound JWT comes from the (future) UI. For unit tests, this is mocked.
- **Object-definition `name` collision.** The picklist field is literally called `name`. If `SEOStudioScan` has an internal Liferay `name` already (it does not, per the current definition), the field would clash. Confirmed clean in §1 of the spec audit.
- **Date semantics.** All `requestDate` comparisons are UTC days. Logs from servers in other zones will be reduced to UTC at parse time — `requestTime` is stored on the parsed `JSONObject` as a fully zoned ISO-8601 instant string ending in `Z`, so the aggregator can substring the leading 10 chars to get the UTC calendar day.
