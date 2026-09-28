# Changelog

All notable changes to this project are recorded here. Versions follow [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-09-28

Modular source, six ready-made profiles, disclosure switches, existence-only modes, and a compatibility pass. With the `full` profile and the same CONFIG values, every viewer sees the same card as in 1.0.0 (measured on one instance: 9 856 viewer, issue and page triples, no difference).

### Added

- Modular source under `src/`: `config/` (values and texts), `core/` (context, decide, entry), `policy/` (internal-mail-domain, internal-group, people-reporter-assignee, jsm), `modes/` (portal, share, moved, secured, a `_template`), `render/` (render.groovy, client.js). Every module declares `provides` and `requires`; a closure only sees what is declared above it, and the assembler keeps that order.
- `build/assemble.py` joins a profile into one deployable file. Six profiles, assembled and committed in `dist/`: `full`, `jsm-only`, `no-jsm`, `secured-minimal`, `group-policy`, `exists-only`. `docs/RECIPES.md` says which one fits which situation; `docs/EXTENDING.md` says how to add a mode or a policy.
- `DISCLOSURE` and `DISCLOSURE_BY_LEVEL`: the restricted card can now withhold the level's name, the field's name, or the people, globally or per level. The gates still run; these only trim what passes them. Two texts for the case where the field exists but is not named: `securedLeadFieldUnnamed`, `securedMessageFieldUnnamed`; the payload carries `hasField`.
- `ESCALATION_BY_PROJECT`: a "raise a request" target per project key for the portal, share, moved and secured cards. The generic card keeps `FALLBACK_URL` on purpose, so a hidden issue and a missing key still get the same card.
- `MODE_ORDER` and `PAGES`: which modes are tried, in which order, and on which pages.
- `SECURED_FIELD_PREFERENCE`: which multi-user field to suggest when a level grants access through several.
- `policy/internal-group`: "internal" defined by group membership (`INTERNAL_GROUPS`) for directories where usernames are not e-mail addresses.
- Existence-only modes `restricted` ("this issue exists and you may not view it", nothing else) and `missing` ("no issue has this key"), for internal viewers with application access, off unless listed in `MODE_ORDER`; `RESTRICTED_SCOPE` (`in-scope`, `all`, `any-issue`); profile `exists-only` at `any-issue`; texts `restrictedTitle`, `restrictedText`, `restrictedTextAgent`, `restrictedOpenAgain`, `missingTitle`, `missingText`. Asked for by a reader who wants no information on restricted issues shared, only "restricted" told apart from "deleted". They are the one deliberate exception to the rule that a hidden issue and a missing key look the same. `missing` answers only at `any-issue` with `restricted` also listed: at a narrower scope it would sit next to generic cards for existing issues and confirm that every one of them exists (measured on one instance before the rule was added). `restricted` repeats the key the viewer used, not the canonical key, so an old key after a move does not reveal the new project. `docs/DESIGN.md`, "Existence only", states the trade-off.
- `docs/COMPATIBILITY.md`: every Jira and Service Management API the fragment calls, checked against the Javadoc of Jira Data Center 8.0.0 to 11.3.4 and Service Management 4.0.0 to 11.3.4, the ScriptRunner pairing per Jira major, and what is confirmed only by the author's instance.
- `CONTRIBUTING.md`, issue templates, and a GitHub Actions check (assemble, client-comment check, test sync).

### Changed

- `GlobalPermissionKey.USE`, deprecated since Jira 7.0, is no longer used. The "has application access" gate of the share, moved and secured cards is `user.isActive()` plus `ApplicationRoleManager.hasAnyRole(user)`, both present unchanged from Jira 8.0 to 11.x. `hasAnyRole` alone does not look at the account's status: on a census of 2 676 accounts on one instance it answered true for 296 deactivated accounts where `USE` answered false; with `isActive()` the two agree for every account. `ApplicationAuthorizationService`, which the deprecation note points to, only answers for one named application (`canUseApplication(user, key)`), so `hasAnyRole` is the call that means "any application". Run the decision test's X08 to X13 after upgrading.
- The entry point's URL routing lives in two pure closures, `route()` and `prefixLinks()`. `route()` asks each regular-expression matcher once with `find()` and reads `group(1)`, so it does not depend on how Groovy coerces a `Matcher` to boolean; 1.0.0 coerced the same matcher twice, and that idiom was measured as correct on Groovy 4.0.8, so this is a readability and testability change, not a fix. `tests/route_test.groovy` pins the routing down for twenty URLs.
- In 1.0.0 an exception inside the portal lookup also stopped the share card from being tried, because both sat in one `try`. In 1.1.0 each mode runs in its own `try`, so after a portal failure the share mode is still tried; both use the same Service Management services, so in practice the outcome is the same.
- `decide()` records the exceptions it swallows when a test asks it to (`MODE_ERRORS`), so a mode that always throws cannot pass a negative test by accident. Deployed, the hook is null and costs nothing.
- The Service Management callable is built with Groovy's `asType` instead of a bare `java.lang.reflect.Proxy`, so the proxy answers `equals`, `hashCode` and `toString` properly.
- `getUsersSecurityLevels`, documented as "can be null", is guarded; the permission scheme lookup is null-checked.
- Permission keys use the `ProjectPermissions` constants instead of string literals.
- The request object stays untyped, so the fragment runs unchanged on Jira 11, where `ExecutingHttpRequest.get()` returns a `jakarta.servlet` request.
- A Jira served under a context path (for example `/jira`) is handled: the entry point strips it before matching the page, treats a URI outside it as no page, and puts it back on every root-relative link the card carries. That 1.0.0 rendered nothing on such an instance follows from reading its code, not from a measurement; 1.1.0 is not verified on such an instance either, only with strings in `tests/route_test.groovy`.
- Scheme and level ids in CONFIG are compared as `Long` whether or not they carry the `L` suffix; per-level `DISCLOSURE_BY_LEVEL` keys likewise.
- Documentation: the fragment type is called "Show a web panel" in ScriptRunner's documentation, not "Custom web panel"; the requirements now state the Javadoc-verified range and the ScriptRunner pairing instead of "Jira 9.x, ScriptRunner 8.x (assumed)".
- `build/sync_tests.py` reads `dist/full.groovy` by default and also syncs the new ROUTE section. `tests/render_test.groovy` gained cases for a hidden field name and the minimal card. `tests/decision_test.groovy` gained X08 to X13 (the application-access gate on its own, including deactivated accounts, with preconditions `needsInternal`, `needsAppRole`, `needsNoAppRole` and `needsSchemeBrowse` that prove no other gate stopped the card), T16 and T17 (a viewer on a request's level who is not a participant), E01 to E09 (the existence-only modes, called directly), preconditions that turn a case whose data does not match the scenario into a SKIP (`needsLevel`, `needsArchived`, `needsInactive`, `needsOnLevel`, `needsOffLevel`, `missing`), an `optional` flag for a case no data on an instance can satisfy (reported as N/A), a `RESULT OK / NOT OK` verdict (a SKIP or an empty run is not a pass; a case without a proper `expect` fails), and fuller expectations. New: `tests/route_test.groovy` and the template `tests/compare_versions.groovy`.

### Removed

- `src/browse-error-helper.groovy`. Its assembled equivalent is `dist/full.groovy`.

## [1.0.0] - 2026-09-26

First public release. Functionally identical to internal version 7.4 (2026-09-24).

### Changed for the public release

With equivalent configuration, none of these changes alters what any viewer sees.

- Every deployment-specific value (links, the request-type field id, scheme and level ids, automation account names) moved into one `CONFIG` block at the top of the fragment, with placeholder defaults that keep the restricted card switched off until they are replaced.
- All user-facing text moved into `CONFIG` as the `TEXT` map, with `{placeholders}` for the dynamic parts. The rendered cards and copy-ready messages are character-for-character the same as 7.4's with the default text.
- The internal-viewer check became a configurable policy: `INTERNAL_MAIL_DOMAINS` (a list of domain patterns) and `INTERNAL_REQUIRE_USERNAME` (default `true`); see [docs/CONFIG.md](docs/CONFIG.md).
- The name tokenizer's Latin letter range is written as JavaScript escapes, so the source file is plain ASCII. The regular expression the browser evaluates is unchanged.
- Tests use synthetic data and are refreshed from the fragment by `build/sync_tests.py`; the comment-stripping build step is a standalone tool, `build/strip_client_comments.py`.

## Internal history

Summarised; the internal versions were not published.

### 7.4 - 2026-09-24

- The build strips every full-line comment from the client script and fails if one survives.
- The lesson: everything inside the client script reaches every browser that gets a card, comments included. Two developer comments, one carrying a real display name (present since version 4) and one an internal issue key (written in 7.0, first shipped in 7.2), had been shipping in the page source. Reviews had read the card's text, the server logic and the client's behaviour, but not the shipped bytes. The fix is structural rather than a rule to remember: explanations live in server-side comments and documentation, and the build removes anything else.

### 7.3 - 2026-09-24

- Removed a maintainer-only preview mode. The card now renders only from the server-side decision.

### 7.2 - 2026-09-23

- Hardening after an independent re-verification of 7.1: the internal-viewer check tightened to require two matching signals instead of one (in 1.0.0, `INTERNAL_REQUIRE_USERNAME`).
- More compartments whose names are the secret added to the exclusion list.
- Scheme ids compared as `Long`, so a list of integers can no longer switch the card off silently.

### 7.1 - 2026-09-23 (not deployed)

- Two independent adversarial reviews of 7.0 found a blocker before release: application access is not proof of being staff, and on some compartments the level's name and the people on the issue are exactly what the level protects. The restricted card would have shown both to accounts that should never learn such issues exist.
- Added the gates that define the design: the internal-viewer policy; schemes in scope with an exclusion list; project not archived and issue editable; "would the permission scheme let the viewer in with issue security left out, or through the suggested field".
- Accounts without an e-mail address fall back to their username in copy-ready messages.
- Copy buttons announce their result to screen readers; the people chips wrap long names.

### 7.0 - 2026-09-23 (not deployed)

- New `secured` card for issues hidden by their security level: says so, points at the level's multi-user picker field that opens this one issue, names up to two people who can fill it in, and offers a copy-ready message. Kill switch `SECURED_CARD`.
- The name tokenizer covers Latin Extended letters, so names with diacritics are greeted correctly; the greeting uses a first name only when the first word needed no cleaning.

### 6 - 2026-09-18

- Covers the Service Management agent view (queues), whose dead end is gated on the agent licence rather than on Browse Projects.
- New `moved` card for issues moved out of a service desk, detected by key history with the request-type field as a fallback (built as version 5, first deployed with 6). Kill switch `MOVED_CARD`.

### 4 - 2026-08-21

Hardening after an adversarial audit:

- The share card requires the viewer to hold application access, so portal-only customers never learn that a request exists.
- The reporter's e-mail address removed from the payload; the display name stays.
- The JSON payload is HTML-escaped before it enters the inline script, so a value containing `</script>` cannot break out.
- The request branch is gated on the project type, not on the request-type field alone, which issues keep after moving out of a service desk.
- The payload defaults to the generic card before any lookup, so a failure degrades to a usable card and "hidden" looks the same as "absent".
- The URL match is case-insensitive and the key is taken from the resolved issue, not from the URL.

### 1 to 3 - 2026-08-21

- The generic card replacing the dead end, the `portal` card for requests the viewer can open on the customer portal, and the `share` card.
