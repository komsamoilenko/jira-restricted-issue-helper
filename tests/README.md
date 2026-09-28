# Tests

Four Script Console scripts and one template. All are read-only: they call the fragment's own functions and write nothing to Jira. Each holds verbatim copies of sections of an assembled file, `dist/full.groovy` as committed, or your configured copy once you refresh them.

| File | Needs | Proves |
|---|---|---|
| `render_test.groovy` | nothing | prints every card variant exactly as the deployed RENDER block emits it, for inspection: `<`, `>` and `&` in values must arrive as their JSON escapes, backslash-u003c, backslash-u003e and backslash-u0026 (the hostile case has a level name, field name, display name and e-mail built to close the script block), and the script must hold no comment line. This test prints; it does not assert. The card's sentences are composed in the browser, so the output shows the payload and the texts, not the finished wording |
| `route_test.groovy` | nothing | the entry point's `route()` names the right page and key for twenty URLs, with and without a context path, and `prefixLinks()` puts the context path on root-relative links only. Asserts; ends with `RESULT OK` |
| `decision_test.groovy` | a few accounts and issues on your instance | `decide()` gives each (viewer, issue, page) the card the design says it should, including every gate of the restricted card (cases T01 to T17, X01 to X07) and the application-access gate on its own (X08 to X13: an internal viewer without application access, and a deactivated account, must always get the generic card; their preconditions prove that no other gate stopped the card). E01 to E09 call the existence-only modes `restricted` and `missing` directly, so they are checked even though `full` leaves them out of `MODE_ORDER` |
| `decision_test_secured_off.groovy` | the same accounts and issues | with `SECURED_CARD = false`, every viewer the restricted card covered gets the generic card again, and the other modes are unchanged (cases F01 to F04) |
| `compare_versions.groovy` | two assembled files and a sample of users and issues | template, not synced: runs the DECIDE section of an old and a new version over the same triples and lists every difference. For a release that promises the same card as before |

The render test covers the portal, share, moved and generic cards on both pages, and the restricted card with two people, one person, a Latin Extended name, nobody to name, no field, no field and an unnamed level, hostile values, and "secured field unnamed": the payload that `DISCLOSURE` with `fieldName: false` produces, where `fieldName` is null and `hasField` is true. That case must show the unnamed field sentence (`securedLeadFieldUnnamed`), the unnamed copy-ready message (`securedMessageFieldUnnamed`) and the copy button, not the "no field" wording.

## Running them

1. Refresh the copied sections from the file you deploy, with your CONFIG values:
   `python build/sync_tests.py --source <your configured file>`
   Without `--source`, the script reads `dist/full.groovy`.
2. For the two decision tests, replace the synthetic accounts and issue keys (`alice@example.com`, `DEMO-101` and so on) with real ones that match each scenario, as described at the top of `decision_test.groovy`, and set the expected level and field names to yours. Drop scenarios your instance cannot express.
3. Open **Administration > ScriptRunner > Console**, paste the whole file, and run it.

The decision tests print `RESULT OK` or `RESULT NOT OK`, then `SUMMARY pass=N fail=N skip=N` and one line per case with what `decide()` returned. The result is OK only when every case ran and passed: a SKIP is not a pass, and an empty run is not a pass. A case is skipped when its account does not exist, when its key does not resolve (a missing key gives the generic card to everyone, which would let a negative case pass for the wrong reason; only T14 declares `missing: true`), or when a precondition the scenario states does not hold (`needsLevel`, `needsArchived`, `needsInactive`). A FAIL means either the logic or your data moved; the `got` line shows which. A line `mode threw:` means a mode raised an exception that the deployed fragment would have swallowed silently; in a test that is a failure.

Preconditions a case can state: `needsLevel` and `needsArchived` (the issue), `needsInactive`, `needsInternal`, `needsAppRole` and `needsNoAppRole` (the account), `needsOnLevel` and `needsOffLevel` (the account against the issue's level), `needsSchemeBrowse` (the permission scheme, with issue security left out, would let the account browse the issue), and `missing` (the key must not resolve). Every unmet precondition is listed in the SKIP line. A case marked `optional: true` whose preconditions are not met is reported as N/A instead and does not spoil the verdict. A `direct:` case (one mode called on its own) is skipped when the viewer can open the issue, because `decide()` would never reach a mode then. Every case must carry `expect`, either `null` or a map that names a `mode`; anything else fails, so a typo cannot pass silently.

A Script Console run that takes more than about thirty seconds may come back as a 504 from a load balancer while the script keeps running on the server. The shipped cases run in a few seconds; if you add many, split them.

Two mutants worth running once on your instance, to see the tests catch a broken build: make `hasAppAccess` return `true` for everyone, and X09 to X13 must fail; make it throw, and the failing cases must each show a `mode threw:` line, which proves the `MODE_ERRORS` hook is wired (it relies on `def MODE_ERRORS` staying inside the copied DECIDE section). `compare_versions.groovy` pastes DECIDE inside a closure, so the hook is out of reach there; a mode that throws shows up only as a difference between the generic card and a card.

To look at a rendered card, save one case's `<script>` from the render test's output into an HTML page that contains `<div class="issue-error"></div>`, and open it in a browser.

Without Python, the committed tests still run: they hold the sections of `dist/full.groovy` with the placeholder values. The render test is then useful as it is. The decision tests need your CONFIG values to mean anything, so refresh them once on any machine with Python.

## Profiles other than `full`

The cases are written for the `full` profile. When you deploy another one, refresh from that file and adjust the expectations it changes:

- `jsm-only`: no case returns `secured`; the T cases that expect it get the generic card, and the kill-switch test proves nothing new.
- `no-jsm`: no case returns `portal`, `share` or `moved`; drop the Service Management scenarios.
- `secured-minimal`: the `secured` cases return an empty `levelName`, a null `fieldName` (with `hasField` true where there is a field), and no `people`.
- `group-policy`: X01 to X03 need accounts outside `INTERNAL_GROUPS` rather than outside the mail domains, and the internal viewer of the other cases must be in one of the groups.
- `exists-only`: sync from `dist/exists-only.groovy` and rewrite the T expectations before running. There is no `secured`, `portal`, `share` or `moved` card: an internal viewer with application access who cannot open the issue gets `restricted` wherever `full` gave one of those (T01, T02, T05 to T07, T10, T12, T13, T16), a viewer who can open the issue still gets no card (T03, T04, T11, T15), and everyone outside the audience keeps the generic card (T08, T09, X01 to X07). T14 expects `missing` there, and E06 expects `missing`; both expectations already follow the CONFIG values, so they need no edit.

The same applies to a profile of your own: a case is right when it matches what your CONFIG says the card should do.

## After upgrading from 1.0.0

The application-access gate of the share, moved and secured cards changed from `GlobalPermissionKey.USE` to `isActive()` plus `ApplicationRoleManager.hasAnyRole`. The cases that make this gate the deciding one are X08 to X13: an internal viewer without application access, and a deactivated internal account that is still in a licensed group, against a restricted issue, a request with a usable reporter and an issue moved out of a service desk. All six must give the generic card. X08 is marked `optional`: it needs an account without any application role that the permission scheme would still let browse a restricted issue, and on some instances no such pair exists; it is then reported as N/A and the verdict ignores it. T08, T09, X01 and X02 cover the same gate for a portal-only customer, an external account and a service account, but other gates also stop those viewers.

## Keeping them honest

The tests contain verbatim copies of an assembled file's sections, never hand-edited ones. Do not edit inside a `// >>> COPY` region; change `src/` or your configured copy and run `build/sync_tests.py`. `python build/sync_tests.py --check` fails if any copy is stale. After a rebuild with `build/assemble.py`, refresh the tests as well.
