# LPD-89681 — AI Bot Requests Log Processor

Specification for the client-extension processor that turns Apache Combined Log Format files into aggregated `SEOStudioAIRequest` rows. Pulls together [LPD-89681](https://liferay.atlassian.net/browse/LPD-89681) under the [LPD-86654](https://liferay.atlassian.net/browse/LPD-86654) parent story, sitting on top of the headless API skeleton already shipped by LPD-88575.

## 1. Objective

Deliver the backend pipeline that ingests an Apache Combined Log Format file uploaded by a Marketing Leader, identifies AI bot traffic, aggregates the counts per (bot, page URL, request date, scan), persists them as `SEOStudioAIRequest` object entries, and keeps the working set bounded to the last 365 days.

Out of scope: the AEO/GEO admin page UI, the file-upload UX, the side-menu wiring (handled by sibling stories under LPD-86654).

### 1.1 User Story

> As a Marketing Leader, I want the portal to read an access log I provide so that I can see, per AI bot, how many times each bot has hit each page in the last year.

### 1.2 Acceptance Criteria

1. Given a valid Apache Combined Log Format file with `name=file` in a multipart upload, when the client extension is called for a domain ID, then it creates a `SEOStudioScan` with `scanType = incremental` and `name = "aiRequestProcessor"`, parses entries newer than the previous successful `aiRequestProcessor` scan (or, if no prior scan exists, newer than `now - 365 days`), aggregates them, writes `SEOStudioAIRequest` rows, transitions the scan to `completed`, and returns the scan ID.
2. Given a malformed log file (e.g., not Combined Log Format, empty, binary), when the client extension is called, then it rejects the request with HTTP 400 and a problem message that names the offending line; no `SEOStudioScan` is created.
3. Given the domain already has a `SEOStudioScan` with `name = "aiRequestProcessor"` in state `queued` or `running`, when a new request arrives for the same domain, then the client extension rejects it with HTTP 409.
4. Given log entries whose `User-Agent` does not match any **enabled** `SEOStudioAIBotConfiguration` for the domain, when aggregation runs, then those entries are excluded from the output.
5. Given multiple log entries that share the same `(agentName, pageURL, requestDate)`, when aggregation runs, then a single `SEOStudioAIRequest` row is written with `count` equal to the sum.
6. Given an `aiRequestProcessor` scan completes successfully, when the client extension transitions the scan to `completed`, then immediately after the PATCH succeeds the client extension deletes `SEOStudioAIRequest` rows for that domain whose `requestDate` is older than 365 days; failed or cancelled scans leave existing data untouched.
7. Given the parser interface, when a future format (e.g., CDN JSON logs) needs to be supported, then a new `LogParser` implementation can be registered without touching the orchestrator.

## 2. Architecture

```
+--------------+   multipart    +-------------------------------------+   headless    +------------------+
|  AEO/GEO UI  | -------------> | liferay-seostudio-airequestprocessor | <-----------> | Liferay portal   |
|  (out of     |  POST /process |  - Pre-checks concurrent scan       |  /o/c/...     |  - SEOStudio*    |
|   scope)     |    file=...    |  - Validates log format             |  /o/seo-...   |    objects       |
|              |    domainId=.. |  - Picks LogParser by format key    |               |  (object API     |
|              |    format=...  |  - Filters by lastScannedDate       |               |   only; no       |
|              |                |  - Filters by enabled bots          |               |   bespoke        |
|              |                |  - Aggregates                       |               |   listeners)     |
|              |                |  - Posts rows + scan state          |               |                  |
|              |                |  - Prunes >365d rows post-complete  |               |                  |
+--------------+                +-------------------------------------+               +------------------+
```

Two deliverables:

| Layer | Module path | What it does |
| --- | --- | --- |
| **Client extension** (new) | `workspaces/liferay-seostudio-workspace/client-extensions/liferay-seostudio-airequestprocessor/` | Spring Boot service. Accepts the log upload, pre-checks for an in-flight scan, validates, parses, aggregates, calls the portal headless API, and prunes rows older than 365 days after the scan transitions to `completed`. |
| **Site initializer** (extend existing) | `modules/dxp/apps/seo-studio/seo-studio-site-initializer/` | Adds a `name` Picklist field to `SEOStudioScan` backed by a new `seo-studio-scan-name-list-type-definition.json` (seeded with `AIRequestProcessor`). No new portal-side Java is shipped by this ticket. |

Headless API surface used by the client extension is the auto-generated object headless API (`/o/c/seostudioscans`, `/o/c/seostudioairequests`, `/o/c/seostudioaibotconfigurations`) plus the existing `AIRequestResource` from LPD-88575. **No new REST Builder endpoints are required for this ticket.**

### 2.1 Sequence

```
UI                    CX                          Portal
 |  POST /process     |                            |
 |  file, domainId,   |                            |
 |  format            |                            |
 |------------------->|                            |
 |                    | validate(file)             |
 |                    | if invalid -> 400          |
 |                    |                            |
 |                    | GET /o/c/seostudioscans?   |
 |                    |   filter=name='aiRequestProcessor',
 |                    |          domainId,
 |                    |          state in (queued,running)
 |                    |--------------------------->|
 |                    |<-- 0 or 1+ scans ----------|
 |                    |   if count > 0 -> 409
 |                    |                            |
 |                    | GET /o/c/seostudioscans?   |
 |                    |   filter=name='aiRequestProcessor', state=completed, latest
 |                    |--------------------------->|
 |                    |<-- scan or empty ----------|
 |                    |   lowerBound = scan?.requestDate ?? now - 365d
 |                    |                            |
 |                    | GET /o/c/seostudioaibotconfigurations?
 |                    |   filter=domainId,enabled  |
 |                    |--------------------------->|
 |                    |<---------------------------|
 |                    | POST /o/c/seostudioscans   |
 |                    |   {scanType=incremental, name=aiRequestProcessor, state=running}
 |                    |--------------------------->|
 |                    |<---- scan { id, ... } -----|
 |                    |                            |
 |                    | parse + filter + aggregate |
 |                    | (in-memory, streaming)     |
 |                    |                            |
 |                    | POST /o/c/seostudioairequests/batch (loop)
 |                    |--------------------------->|
 |                    |<---------------------------|
 |                    | PATCH /o/c/seostudioscans/{id}
 |                    |   {state=completed}        |
 |                    |--------------------------->|
 |                    |                            |
 |                    | DELETE /o/c/seostudioairequests/{id} (loop)
 |                    |   for each row where domainId matches
 |                    |   and requestDate < scan.requestDate - 365d
 |                    |--------------------------->|
 |<--- 200 scanId ----|                            |
```

On any failure after the scan is created, the CX must `PATCH` the scan to `state=failed` with `errorMessage` populated; the existing scan state-flow already allows `running → failed`. The post-complete prune is best-effort — a prune failure logs but does not flip the scan back to `failed`, since the scan itself succeeded and the rows being deleted are stale data.

### 2.2 Parser Strategy

```java
public interface LogParser {
    String formatKey();
    Stream<JSONObject> parse(InputStream in, Instant lowerBound)
        throws InvalidLogFormatException;
}
```

Each `JSONObject` carries the keys the aggregator needs: `userAgent`, `requestPath`, and `requestTime` (ISO-8601 UTC string). Using `org.json.JSONObject` rather than a dedicated record keeps the CX's value shape consistent with the rest of the codebase's JSON-heavy style and avoids the only Java `record` on this branch. The `lowerBound` parameter lets the parser short-circuit on lines older than the cutoff (see §2.3.1). Implementations are picked up as Spring beans; the orchestrator selects one by `format` query parameter (default `apache-combined`). Only `apache-combined` is implemented in this ticket; the interface is the future-proofing. Error reporting on malformed lines goes through `InvalidLogFormatException.getLine()`, not the returned `JSONObject` — so the raw line never lives past the failing-line frame.

`ApacheCombinedLogParser` makes one strong assumption: **logs are sorted oldest-first** (the Apache and nginx default). With that assumption it spools the upload to a `Files.createTempFile` location, then reads lines newest-first via Apache Commons IO's `ReversedLinesFileReader`. A cheap timestamp pre-extract on each line decides whether to drop into the full regex; the first line older than `lowerBound` terminates the stream because every earlier line is also older. The temp file is deleted when the returned stream is closed (and via `File.deleteOnExit()` as a fallback if the JVM dies mid-parse). The net result is that re-uploading the same file after a completed scan returns an empty stream after one pre-extract check, instead of fully parsing every line only to filter it out downstream. If a future log source returns newest-first or unsorted, that parser needs a different impl — the SPI is unchanged, just the assumption.

### 2.3 Aggregation Key

`(agentName, pageURL, requestDate, scanId)`. The CX builds a `Map<AggKey, Integer>` in memory; for the first cut we accept the working set fits in RAM (Apache logs at a few million lines/day, after AI-bot filtering, easily fit). `pageURL` is the request path only (no host, no query string) to keep the cardinality low. `requestDate` is the calendar day in UTC.

### 2.3.1 Time Window

The processor's lower bound for which log entries to keep is:

```
lowerBound = (previousCompletedAIRequestProcessorScan?.requestDate) ?? (now - 365 days)
```

The orchestrator passes `lowerBound` to `LogParser.parse(in, lowerBound)`. The parser is responsible for dropping below-bound entries; `ApacheCombinedLogParser` does this by reading lines newest-first and terminating its stream the first time a pre-extracted timestamp is below the cutoff (see §2.2). The upper bound is the scan's own `requestDate` (set at creation), and is naturally satisfied because no log entry can be timestamped after the moment of scan creation. In practice this means a domain's first-ever upload is bounded to the trailing 365 days; subsequent uploads only re-process logs newer than the last successful scan, so re-uploading the same file is a no-op after the first run and exits the parser after a single pre-extract check.

### 2.4 Pruning

Pruning is CX-side and synchronous within the `POST /process` request. Immediately after the CX successfully PATCHes the scan to `state = completed`, it issues a `GET /o/c/seostudioairequests` filtered by `r_seoStudioDomainTo... eq <domainId> and requestDate lt <scan.requestDate − 365 days>`, then iterates the page and calls `DELETE /o/c/seostudioairequests/{id}` on each result. Failed or cancelled scans skip the prune step entirely.

Implementation notes:

- The DELETE loop runs inside the request thread so the `200 scanId` response is only returned once pruning finishes. A separate executor isn't worth the operational complexity for a deletion bounded by one domain's stale data (one day's rows per typical run; a year's rows on the very first upload).
- Prune failures are logged at `WARN` and do not flip the scan back to `failed`. The scan itself succeeded; stale-data cleanup is a follow-on concern.
- No portal-side listener is registered for this ticket. If a future caller mutates `SEOStudioScan` to `completed` outside of the CX (e.g., a manual REST patch), no pruning fires. This is acceptable because the only completing-caller in scope is the CX itself.

Implication: a domain that stops uploading logs keeps its old data forever — pruning only happens on successful new scans driven by the CX. That matches the product intent (no surprise data loss for inactive domains) and removes the need for a daily scheduler.

## 3. Commands

### Client Extension

```bash
cd workspaces/liferay-seostudio-workspace
./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootJar
./gradlew :client-extensions:liferay-seostudio-airequestprocessor:buildClientExtensionDockerImage
./gradlew :client-extensions:liferay-seostudio-airequestprocessor:bootRun
./gradlew :client-extensions:liferay-seostudio-airequestprocessor:test
```

### Portal App

```bash
cd modules/dxp/apps/seo-studio/seo-studio-site-initializer
gw deploy -a
```

The site initializer is the only portal-side deliverable. `seo-studio-web` is not touched by this ticket.

### Format Source

```bash
# from portal-impl, before the last commit on the branch
ant format-source-current-branch
```

## 4. Project Structure

```
workspaces/liferay-seostudio-workspace/
├── client-extensions/
│   └── liferay-seostudio-airequestprocessor/
│       ├── Dockerfile
│       ├── LCP.json
│       ├── build.gradle
│       ├── client-extension.yaml
│       └── src/
│           ├── main/
│           │   ├── java/com/liferay/seo/studio/airequestprocessor/
│           │   │   ├── AIBotConfigurationClient.java
│           │   │   ├── AIRequestAggregator.java
│           │   │   ├── AIRequestClient.java
│           │   │   ├── AIRequestProcessorRestController.java          # POST /process
│           │   │   ├── AggregationKey.java
│           │   │   ├── ApacheCombinedLogParser.java
│           │   │   ├── BotMatcher.java
│           │   │   ├── InvalidLogFormatException.java
│           │   │   ├── LogParser.java                                 # strategy SPI
│           │   │   ├── PortalClient.java
│           │   │   ├── ReadyRestController.java
│           │   │   ├── ScanClient.java
│           │   │   └── SeoStudioSpringBootApplication.java
│           │   └── resources/
│           │       ├── application-default.properties
│           │       └── application.properties
│           └── test/
│               └── java/com/liferay/seo/studio/airequestprocessor/
│                   ├── AIRequestAggregatorTest.java
│                   ├── AIRequestProcessorRestControllerTest.java
│                   ├── ApacheCombinedLogParserTest.java
│                   └── BotMatcherTest.java
├── gradle/
├── gradle.properties
├── package.json
└── settings.gradle

modules/dxp/apps/seo-studio/
└── seo-studio-site-initializer/
    └── src/main/resources/site-initializer/
        ├── list-type-definitions/
        │   └── seo-studio-scan-name-list-type-definition.json         # NEW: seeded with AIRequestProcessor
        └── object-definitions/
            └── 03-seo-studio-scan-object-definition.json              # add "name" Picklist field
                                                                       #      backed by L_SEO_STUDIO_SCAN_NAMES
```

No new portal-side Java is shipped by this ticket. Concurrency (AC3) and pruning (AC6) live entirely in the client extension; the only portal-side change is the site initializer schema delta above. If a future caller needs to enforce these invariants outside the CX (a second client extension, a direct REST call, a batch import), a portal-side `SEOStudioScanObjectEntryModelListener` can be added in a follow-up — its absence here is a deliberate scope decision, not a gap.

### 4.1 `client-extension.yaml`

```yaml
assemble:
    -   fromTask: bootJar
liferay-seostudio-airequestprocessor-oahs:
    .serviceAddress: localhost:58081
    .serviceScheme: http
    name: Liferay SEO Studio AI Request Processor OAuth Application Headless Server
    scopes:
        -   Liferay.Object.Admin.REST.everything
    type: oAuthApplicationHeadlessServer
liferay-seostudio-airequestprocessor-oaua:
    .serviceAddress: localhost:58081
    .serviceScheme: http
    name: Liferay SEO Studio AI Request Processor OAuth Application User Agent
    type: oAuthApplicationUserAgent
```

There is no `objectAction` registration this time — the UI calls the client extension directly with the uploaded file. Two OAuth applications are registered to mirror the sibling `liferay-seostudio-pagespeed` layout:

- `*-oaua` (`oAuthApplicationUserAgent`) is the default for return calls into the portal headless API. The UI forwards the user's JWT; the CX uses it so portal permission checks run as the uploading user.
- `*-oahs` (`oAuthApplicationHeadlessServer`) is the service identity, requested from `LiferayOAuth2AccessTokenManager` only when an operation must run without a user context (e.g., a future background reconciliation job, or any call to an endpoint the user cannot reach). Today's `POST /process` path stays on the user-agent token; the headless-server registration is structural parity, not a hot path.

`Liferay.Object.Admin.REST.everything` is the only scope wired on the headless server token because every endpoint the CX touches (`/o/c/seostudioscans`, `/o/c/seostudioaibotconfigurations`, `/o/c/seostudioairequests`) is part of the auto-generated object REST surface.

## 5. Code Style

Inherit the [Liferay portal conventions](.claude/CLAUDE.md) plus the additions in `~/dev/projects/CLAUDE.md`. The points that bite hardest in this code base:

- **No method chaining** except on builders (`HashMapBuilder`, `JSONUtil`, `PortletURLBuilder`, `StringBundler`), `Stream`, `Optional`, and Mockito.
- **No wildcard / static imports.** `HtmlUtil`, `StringUtil`, `Objects.equals`, `JSONUtil.put` over their JDK counterparts.
- **Alphabetical declarations** within a chunk (no blank lines between them).
- **Variable name matches type**: `Layout layout`, `LogParser logParser`, collections plural, maps `*Map`.
- **No `@Reference` chains** off `computeIfAbsent`; assign first.
- **No new enums** — `String` constants or polymorphism.
- **Generated files** (`Base*ResourceImpl.java`, anything tagged `@Generated("")`) must never be hand-edited. Use `gw buildREST` and commit the regenerated output as a separate `LPD-89681 Build REST` commit.
- **Run `/format-source`** before every commit; it is the source of truth.

### Spring Boot Specifics

The client extension is a Spring Boot 2.7.18 app on JDK 21, structured to mirror the sibling `liferay-seostudio-pagespeed` extension introduced by [LPD-88837](https://liferay.atlassian.net/browse/LPD-88837) ([PR #3286](https://github.com/liferay-site-management/liferay-portal/pull/3286)). Concretely:

- The workspace is `workspaces/liferay-seostudio-workspace/` with `settings.gradle` applying `com.liferay.workspace`, plus `gradle.properties`, `package.json`, and the gradle wrapper.
- The module directory is `liferay-seostudio-<purpose>` (one-word `seostudio`); the Java package is `com.liferay.seo.studio.<purpose>` (dotted `seo.studio`); both flat — no `controller/`, `parser/`, `aggregator/`, or `portal/` subpackages.
- The Spring Boot entry point is `SeoStudioSpringBootApplication` (note `Seo`, not `SEO`), annotated `@Import(ClientExtensionUtilSpringBootComponentScan.class)` and `@SpringBootApplication`.
- Controllers extend `com.liferay.client.extension.util.spring.boot3.BaseRestController` and follow the `<Purpose>RestController` naming. `ReadyRestController` exposes `GET /ready → "READY"` for liveness/readiness probes on port 58081.
- `build.gradle` applies `com.liferay.source.formatter`, `java-library`, and `org.springframework.boot`; pulls `com.liferay.client.extension.util.spring.boot3`, `com.liferay.petra.string`, `com.liferay.portal.kernel`, `org.apache.commons.logging`, `org.json:json`, plus Spring Boot `spring-boot-starter-web` and `spring-boot-starter-oauth2-resource-server`.
- `application-default.properties` carries LXC config (`com.liferay.lxc.dxp.*`), the OAuth client-secret env-var binding for the headless server, the comma-separated list of registered OAuth application ERCs, `liferay.oauth.urls.excludes=/ready`, and `server.port=58081`. `application.properties` imports the default plus the `LIFERAY_ROUTES_*` configtrees. Concretely, the OAuth lines look like:

    ```properties
    liferay-seostudio-airequestprocessor-oahs.oauth2.headless.server.client.secret=${LIFERAY_SEOSTUDIO_AIREQUESTPROCESSOR_OAHS_CLIENT_SECRET:}

    liferay.oauth.application.external.reference.codes=\
        liferay-seostudio-airequestprocessor-oahs,\
        liferay-seostudio-airequestprocessor-oaua
    ```

    Both ERCs from §4.1 must appear in `liferay.oauth.application.external.reference.codes` or the corresponding token request will fail at runtime. The client-secret line binds the headless server's secret to a `LIFERAY_*_OAHS_CLIENT_SECRET` env var that LXC injects at deploy time; the user-agent application does not carry a secret in this file because its tokens are minted from the inbound user JWT.
- Use constructor injection (or Spring's `@Autowired` field injection for service-only references, as the PR does for `LiferayOAuth2AccessTokenManager`) — never mix idioms within one class.
- Log via `org.apache.commons.logging.Log`/`LogFactory`. JSON manipulation via `org.json`.
- Stream the upload — never `getBytes()` the whole file into memory.
- All blocking I/O on `Schedulers.boundedElastic()` if reactive types are introduced; otherwise plain `ThreadPoolTaskExecutor`. **Default to plain executor**; keep this app non-reactive unless we measure a need.

## 6. Testing Strategy

| Layer | Where | What |
| --- | --- | --- |
| Unit (parser) | `liferay-seostudio-airequestprocessor/src/test` | `ApacheCombinedLogParserTest`: golden lines, malformed lines, IPv6, non-ASCII paths, missing user agent, multi-line truncation. |
| Unit (aggregator) | same | `AIRequestAggregatorTest`: sum across keys, day boundary, UTC vs. local. |
| Unit (bot matcher) | same | `BotMatcherTest`: exact match, case sensitivity, substring vs. token boundary, disabled config skipped. |
| Unit (controller) | same | `AIRequestProcessorRestControllerTest`: 200 happy path, 400 malformed, 409 concurrent (pre-check returns a queued scan), and a successful path that issues the post-complete prune calls for the right `requestDate` cutoff — all using `MockMvc` and a mocked `PortalClient`. |
| Integration (REST) | `seo-studio-rest-test/src/testIntegration` | Extend `AIRequestResourceTest` to cover aggregated reads against fixtures the processor would write. |

**Test execution mandate**: never start an integration test run without confirming a deployable server already exists. Unit tests under the client extension are free to run.

Fixtures live under `src/test/resources/logs/`:

- `apache-combined-valid.log` — 200 lines, mixed bots and humans, two days.
- `apache-combined-malformed.log` — one bad line per failure mode.
- `apache-combined-large.log.gz` — 1M lines, used only by an opt-in `@Tag("perf")` test.

## 7. Boundaries

### 7.1 Always

- Run `/format-source` before every commit.
- Lead every commit title with `LPD-89681`; sentence case; under 72 chars; no trailing period.
- Group commits by module — API ↔ impl, generated code, and consumer changes in separate commits.
- After editing `rest-config.yaml` or `rest-openapi.yaml`, immediately run `gw buildREST` and commit the regenerated artifacts before any implementation edit.
- After editing `Language.properties`, edit only the global file at `modules/apps/portal-language/portal-language-lang/src/main/resources/content/Language.properties` (never a module-local copy), then run `gw buildLang` and commit the generated locale files as a separate `LPD-89681 Build lang` commit.
- Use `@Reference` for OSGi service injection in `@Component` classes (never `*ServiceUtil`).
- Filter by `enabled=true` when reading `SEOStudioAIBotConfiguration` — never trust the full list.
- Stream the upload; bound memory deliberately.
- Treat `requestDate` as a UTC calendar day; do all date arithmetic in UTC.
- Use the auto-generated object headless API (`/o/c/...`) before reaching for new REST endpoints.

### 7.2 Ask First

- Adding a new `*-rest-api` / `*-rest-impl` / `*-rest-client` / `*-rest-test` quartet (the LPD-88575 quartet should cover this work).
- Adding a new top-level object definition (the existing six in `seo-studio-site-initializer` should cover this work).
- Schema changes to existing `SEOStudioAIRequest` / `SEOStudioScan` / `SEOStudioAIBotConfiguration` object definitions.
- Bumping any dependency version outside the client extension module.
- Adding a new dependency to the portal-side modules.
- Shipping this without the `seo-studio-2.0` feature flag check (the existing `SEOStudioFeatureFlagListener` is what gates the surface).

### 7.3 Never

- Hand-edit a `@Generated("")` file.
- Commit secrets, OAuth tokens, or sample log files with real IP addresses; scrub fixtures to `203.0.113.x` / `2001:db8::x`.
- Skip `--no-verify`, `--no-gpg-sign`, or pre-commit hooks.
- Force-push to `master` or to any shared branch.
- Add `Co-Authored-By: Claude ...` to commits.
- Persist the uploaded log file to disk on the portal server — the client extension keeps it in memory or in `Files.createTempFile` with `DELETE_ON_CLOSE`.
- Aggregate by full URL — strip query string and host before keying, to keep cardinality manageable.
- Introduce a new enum type for parser format keys — use string constants.
- Process the entire log on every refresh — always filter by the previous scan's `requestDate` lower bound.
- Run integration tests without first confirming with the user that a testable server is up.

## 8. Open Questions Resolved by Default

These are decisions made in the spec rather than asked back. Any of them can be overridden by the user; flag them at PR review if anything below feels wrong.

1. **Transport.** Multipart upload directly to the client extension (`POST /process` with `file`, `domainId`, `format`). No portal-side proxy. **Rationale**: matches AC2's "Refresh data" UX and keeps log bytes out of the portal heap.
2. **Authentication.** The CX registers both an `oAuthApplicationUserAgent` (`*-oaua`) and an `oAuthApplicationHeadlessServer` (`*-oahs`) — same shape as the sibling `liferay-seostudio-pagespeed` extension. The UI passes the user's JWT to the CX, which Spring's resource server validates and which the CX uses for return calls into the portal headless API so permission checks run as the uploading user. The `*-oahs` token (requested from `LiferayOAuth2AccessTokenManager`) is reserved for operations that must run without a user context (none today; reserved for a future reconciliation job or any endpoint the user cannot reach). **Rationale**: structural parity with `liferay-seostudio-pagespeed` keeps both workspaces' OAuth configs uniform and avoids re-registering the headless server later when an unscoped operation is added.
3. **Concurrency.** Enforced CX-side by a pre-check: before creating a new scan, the CX queries `GET /o/c/seostudioscans` for `name = "aiRequestProcessor"` scans on the same domain in `state in (queued, running)` and returns HTTP 409 if any exist. **Rationale**: keeps all of LPD-89681 inside the client extension and avoids shipping a portal-side `BaseModelListener` for a single ticket. The CX is the only caller in scope; if a future actor begins creating scans directly via the auto-generated REST endpoint, a portal-side `SEOStudioScanObjectEntryModelListener` should be added as a follow-up since the CX pre-check only protects the CX path. The pre-check has a wider race window than a transaction-scoped listener would; AC3's "single running scan" is therefore a soft invariant (rare-race may admit a duplicate), which the product owners accepted at scope review.
4. **Pruning location and trigger.** CX-side, fired immediately after the CX PATCHes the scan to `state = completed`. No portal-side listener; no scheduled job. The CX issues `GET /o/c/seostudioairequests` filtered to the domain and `requestDate lt scan.requestDate − 365 days`, then iterates the page and calls `DELETE /o/c/seostudioairequests/{id}` per row. **Rationale**: same as concurrency — collapses the ticket to one deployable. The downside is that any future non-CX completer (manual REST PATCH, batch import) leaves data unpruned; in scope, only the CX completes scans.
5. **Scan typing.** No new entry in the scan-types list. Reuse `scanType = incremental` and identify the pipeline by a new `name` **Picklist** field on `SEOStudioScan`, backed by a new `seo-studio-scan-name-list-type-definition.json` (external reference code `L_SEO_STUDIO_SCAN_NAMES`) seeded with the single entry `{key: "aiRequestProcessor", name: "AIRequestProcessor"}`. **Rationale**: a Picklist (rather than a free-form String) keeps `name` values disciplined across pipelines, prevents typos in concurrency-key comparisons, and lets future pipelines (e.g., `sitemapProcessor`) be added by appending a list entry — no schema migration needed.
6. **Time-window fallback.** When no completed `aiRequestProcessor` scan exists for the domain, the processor defaults the lower bound to `now - 365 days`. **Rationale**: the first run for a domain has no checkpoint, but the product cap is one year — the fallback aligns the bootstrap case with the same window that pruning enforces, so the system has a single 365-day invariant rather than two.
6. **Parser dispatch.** Format key comes from a `?format=` query parameter; default `apache-combined`. **Rationale**: future formats will be added by registering a new `LogParser` bean and exposing a key — no controller change.
7. **Aggregation cardinality.** `pageURL` = request path only (no host, no query). **Rationale**: keeps `SEOStudioAIRequest` row count proportional to `|pages| × |bots| × |days|`, not `× |query strings|`.

## 9. References

- [LPD-89681](https://liferay.atlassian.net/browse/LPD-89681) — this ticket.
- [LPD-86654](https://liferay.atlassian.net/browse/LPD-86654) — parent story, "AI bots requests visualization".
- [LPD-88575](https://liferay.atlassian.net/browse/LPD-88575) — sibling, "Add Headless API for AI bots requests" (already merged on this branch).
- [LPD-90135](https://liferay.atlassian.net/browse/LPD-90135) — "Create shared SEOStudio objects" (closed).
- [LPD-88837](https://liferay.atlassian.net/browse/LPD-88837) — sibling under LPD-86654 introducing the `liferay-seostudio-pagespeed` extension; [PR #3286](https://github.com/liferay-site-management/liferay-portal/pull/3286) is the canonical layout reference for this workspace.
- `workspaces/liferay-seostudio-workspace/client-extensions/liferay-seostudio-pagespeed/` — canonical structural reference (workspace, module, package layout, Spring Boot wiring) once LPD-88837 lands.
- `workspaces/liferay-aihub-workspace/client-extensions/liferay-aihub-etc-spring-boot/` — secondary reference Spring Boot CX in this repo.
- `modules/dxp/apps/seo-studio/seo-studio-rest-impl/src/main/java/com/liferay/seo/studio/rest/internal/resource/v1_0/AIRequestResourceImpl.java` — existing aggregated read endpoint.
- AI Hub Crawler Architecture wiki (Confluence; not crawlable without UI auth, link in ticket description).
