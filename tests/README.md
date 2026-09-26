# Tests

Three Script Console scripts. All are read-only: they call the fragment's own functions and write nothing to Jira.

| File | Needs | Proves |
|---|---|---|
| `render_test.groovy` | nothing | prints every card variant exactly as the deployed RENDER block emits it, for inspection: `<`, `>` and `&` in values must arrive as `<`, `>` and `&` (the hostile case has a level name, field name, display name and e-mail built to close the script block), and the script must hold no comment line. This test prints; it does not assert |
| `decision_test.groovy` | a few accounts and issues on your instance | `decide()` gives each (viewer, issue, page) the card the design says it should, including every gate of the restricted card |
| `decision_test_secured_off.groovy` | the same accounts and issues | with `SECURED_CARD = false`, every viewer the restricted card covered gets the generic card again, and the other modes are unchanged |

## Running them

1. Refresh the copied sections from the fragment you deploy, with your CONFIG values:
   `python build/sync_tests.py --source <your configured fragment>`
2. For the two decision tests, replace the synthetic accounts and issue keys (`alice@example.com`, `DEMO-101` and so on) with real ones that match each scenario, as described at the top of `decision_test.groovy`, and set the expected level and field names to yours. Drop scenarios your instance cannot express.
3. Open **Administration > ScriptRunner > Console**, paste the whole file, and run it.

The decision tests print `SUMMARY pass=N fail=N skip=N` followed by one line per case with what `decide()` returned. A case whose account does not exist is a SKIP. A FAIL means either the logic or your data moved; the `got` line shows which.

To look at a rendered card, save one case's `<script>` from the render test's output into an HTML page that contains `<div class="issue-error"></div>`, and open it in a browser.

## Keeping them honest

The tests contain verbatim copies of the fragment's sections, never hand-edited ones. Do not edit inside a `// >>> COPY` region; change the fragment and run `build/sync_tests.py`. `python build/sync_tests.py --check` fails if any copy is stale.
