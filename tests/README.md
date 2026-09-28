# Tests

Three Script Console scripts. All are read-only: they call the fragment's own functions and write nothing to Jira. Each holds verbatim copies of sections of an assembled file, `dist/full.groovy` as committed, or your configured copy once you refresh them.

| File | Needs | Proves |
|---|---|---|
| `render_test.groovy` | nothing | prints every card variant exactly as the deployed RENDER block emits it, for inspection: `<`, `>` and `&` in values must arrive as `<`, `>` and `&` (the hostile case has a level name, field name, display name and e-mail built to close the script block), and the script must hold no comment line. This test prints; it does not assert |
| `decision_test.groovy` | a few accounts and issues on your instance | `decide()` gives each (viewer, issue, page) the card the design says it should, including every gate of the restricted card (cases T01 to T15 and X01 to X07) |
| `decision_test_secured_off.groovy` | the same accounts and issues | with `SECURED_CARD = false`, every viewer the restricted card covered gets the generic card again, and the other modes are unchanged (cases F01 to F04) |

The render test covers the portal, share, moved and generic cards on both pages, and the restricted card with two people, one person, a Latin Extended name, nobody to name, no field, no field and an unnamed level, hostile values, and "secured field unnamed": the payload that `DISCLOSURE` with `fieldName: false` produces, where `fieldName` is null and `hasField` is true. That case must show the unnamed field sentence (`securedLeadFieldUnnamed`), the unnamed copy-ready message (`securedMessageFieldUnnamed`) and the copy button, not the "no field" wording.

## Running them

1. Refresh the copied sections from the file you deploy, with your CONFIG values:
   `python build/sync_tests.py --source <your configured file>`
   Without `--source`, the script reads `dist/full.groovy`.
2. For the two decision tests, replace the synthetic accounts and issue keys (`alice@example.com`, `DEMO-101` and so on) with real ones that match each scenario, as described at the top of `decision_test.groovy`, and set the expected level and field names to yours. Drop scenarios your instance cannot express.
3. Open **Administration > ScriptRunner > Console**, paste the whole file, and run it.

The decision tests print `SUMMARY pass=N fail=N skip=N` followed by one line per case with what `decide()` returned. A case whose account does not exist is a SKIP. A FAIL means either the logic or your data moved; the `got` line shows which.

To look at a rendered card, save one case's `<script>` from the render test's output into an HTML page that contains `<div class="issue-error"></div>`, and open it in a browser.

Without Python, the committed tests still run: they hold the sections of `dist/full.groovy` with the placeholder values. The render test is then useful as it is. The decision tests need your CONFIG values to mean anything, so refresh them once on any machine with Python.

## Profiles other than `full`

The cases are written for the `full` profile. When you deploy another one, refresh from that file and adjust the expectations it changes:

- `jsm-only`: no case returns `secured`; the T cases that expect it get the generic card, and the kill-switch test proves nothing new.
- `no-jsm`: no case returns `portal`, `share` or `moved`; drop the Service Management scenarios.
- `secured-minimal`: the `secured` cases return an empty `levelName`, a null `fieldName` (with `hasField` true where there is a field), and no `people`.
- `group-policy`: X01 to X03 need accounts outside `INTERNAL_GROUPS` rather than outside the mail domains, and the internal viewer of the other cases must be in one of the groups.

The same applies to a profile of your own: a case is right when it matches what your CONFIG says the card should do.

## After upgrading from 1.0.0

The application-access gate of the share, moved and secured cards changed from `GlobalPermissionKey.USE` to `ApplicationRoleManager.hasAnyRole`. Run T08, T09, X01 and X02 against real accounts: a portal-only customer, an external account with application access and a service account must all still get the generic card.

## Keeping them honest

The tests contain verbatim copies of an assembled file's sections, never hand-edited ones. Do not edit inside a `// >>> COPY` region; change `src/` or your configured copy and run `build/sync_tests.py`. `python build/sync_tests.py --check` fails if any copy is stale. After a rebuild with `build/assemble.py`, refresh the tests as well.
