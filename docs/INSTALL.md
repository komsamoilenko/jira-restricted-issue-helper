# Install, upgrade and rollback

## Requirements

- Jira Data Center 9.x
- ScriptRunner for Jira 8.x, with Script Fragments (8.x is the assumed range, not a tested one; see the README)
- Jira administrator rights (to create the fragment and use the Script Console)
- Python 3.10 or later on the machine where you prepare the file (for the two build scripts; no packages needed)
- Optional: Jira Service Management, for the `portal`, `share` and `moved` cards

## 1. Configure

Edit the `CONFIG` block at the top of `src/browse-error-helper.groovy`. Every value is described in [CONFIG.md](CONFIG.md). At a minimum:

- `FALLBACK_URL`: your "raise a request" link;
- `RT_FIELD`: the id of your Customer Request Type field (if you run Service Management);
- `INTERNAL_MAIL_DOMAINS` and `INTERNAL_REQUIRE_USERNAME`: who counts as internal;
- `SECURED_SCHEMES` and `SECURED_SKIP_LEVELS`: where the restricted card may appear, and where it never may.

Keep the configured copy of the file somewhere private if its values describe your instance; the repository copy holds placeholders only.

## 2. Check and test

```
python build/strip_client_comments.py src/browse-error-helper.groovy --check
python build/sync_tests.py
```

The first command must end with `CLEAN`. The second copies your configured sections into the tests. Then run the tests in the Script Console as described in [tests/README.md](../tests/README.md):

1. `tests/render_test.groovy` needs no data and shows every card variant as the browser receives it.
2. `tests/decision_test.groovy` needs a few real accounts and issues that match its scenarios; replace the synthetic ones first.
3. `tests/decision_test_secured_off.groovy` proves the kill switch.

All three are read-only.

## 3. Create the fragment

In Jira, go to **Administration > ScriptRunner > Fragments** and create a new fragment of type **Custom web panel**:

| Setting | Value | Why |
|---|---|---|
| Note | a name you will recognise, for example "Restricted-issue helper" | shown in the fragments list |
| Location | `atl.header.after.scripts` | included in the head of every decorated page, including the dead-end page of the Service Management agent view, which is stock Jira |
| Key | any unique key, for example `restricted-issue-helper` | identifies the fragment |
| Weight | `100` | the value the fragment was built and verified with; see [CONFIG.md](CONFIG.md) |
| Condition | none | the fragment decides for itself; on every other page it returns after one regular expression on the request URI |
| Script | the panel's inline script: paste the whole configured file | the fragment writes its output through the `writer` binding that ScriptRunner provides to web panels |

Save, then check:

1. Open an issue you can see: no card, and no `jbh-style` element in the page.
2. Open `/browse/NOPE-99999` (any key that does not exist): the generic card.
3. Open a restricted issue as a test account that should get the restricted card, and as one that should not.
4. View the page source of a card page and confirm the inline script has no comment lines.

## Upgrade

1. Save the currently deployed body to a file: it is your rollback copy.
2. Take the new release's `src/browse-error-helper.groovy` and carry your CONFIG values over. The CONFIG block is the only part you should need to merge; compare it with your previous one, because a release can add a value.
3. Run the check and the tests (step 2 above) against the merged file.
4. Replace the fragment's script with the merged file and save.
5. Open the fragment again and compare the saved body with your file (a checksum is enough), so you know the running code is the tested code. Never edit the live body by hand; change your file and redeploy it.

## Rollback

Three routes, from lightest to strongest:

1. **Switch a mode off.** Set `SECURED_CARD` (or `MOVED_CARD`) to `false` in CONFIG and redeploy. Every viewer that mode covered gets the generic card again; `tests/decision_test_secured_off.groovy` proves this for the restricted card.
2. **Redeploy the previous body** you saved before the upgrade.
3. **Disable the fragment** with one toggle in the fragments list. Jira's stock pages return immediately.

## Uninstall

Disable or delete the fragment. It stores nothing: no database rows, no properties, no files.
