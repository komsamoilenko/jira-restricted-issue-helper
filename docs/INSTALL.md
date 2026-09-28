# Install, upgrade and rollback

You install one file: `dist/<profile>.groovy`, with your values in its `CONFIG` block, pasted into one ScriptRunner fragment. The modules in `src/` never go to Jira on their own.

There are two ways to get that file:

- **Without Python.** Take one of the five assembled profiles in `dist/` as it is and edit only its `CONFIG` block.
- **With Python 3.10 or later.** Change a profile or the modules in `src/`, rebuild with `build/assemble.py`, and run the build and test tools before you deploy.

## Requirements

- **Jira Data Center 8.0 to 11.x by Javadoc; run in production by the author on 9.x with ScriptRunner 8.x; other combinations not tested.** Every Jira API call the fragment makes is present with the same signature in the official Javadoc of Jira Data Center 8.0.0 to 11.3.4, and every Service Management call in Jira Service Management 4.0.0 to 11.3.4, checked on 2026-09-28. That is a check of the documentation, not a test run.
- **ScriptRunner for Jira** with Script Fragments, in the major that Marketplace lists for your Jira:

  | Jira Data Center | Jira Service Management | ScriptRunner (per Marketplace) | Groovy |
  |---|---|---|---|
  | 8.x | 4.x | 6.x, 7.x or 8.x | 2.5.11 (6.x), 3.0.12 (7.x), 4.0.7 (8.x) |
  | 9.x | 5.x | 8.x (7.x up to Jira 9.7.2) | 4.0.7 (8.x), 3.0.12 (7.x) |
  | 10.x | 10.x | 9.x only | 4 |
  | 11.x | 11.x | 10.x only | 4 |

- Jira administrator rights, to create the fragment and use the Script Console.
- Optional: Jira Service Management, for the `portal`, `share` and `moved` cards.
- Optional: Python 3.10 or later, standard library only, on the machine where you prepare the file. It is needed to rebuild a profile and to refresh the tests with your values; it is not needed to install a `dist/` file.

On Jira 10.x and 11.x, confirm first that the location `atl.header.after.scripts` exists on your instance, with ScriptRunner's Fragment Locator. The location is not documented by Atlassian or Adaptavist; it is seen in Jira sources from 7.1 to 9.12. See [COMPATIBILITY.md](COMPATIBILITY.md).

## 1. Choose a profile

| Profile | Use it when |
|---|---|
| `full` | you run Service Management and use issue security levels. This is what the author runs |
| `jsm-only` | you run Service Management and do not want the helper to talk about security levels |
| `no-jsm` | you run Jira Software or Jira Core without Service Management |
| `secured-minimal` | you want the restricted card, but without the level's name, the field's name or the people |
| `group-policy` | your usernames are logins rather than e-mail addresses, and a group equals staff |

[RECIPES.md](RECIPES.md) goes through more situations. If none fits, [EXTENDING.md](EXTENDING.md) explains how to assemble your own.

## 2. Configure

Copy `dist/<profile>.groovy` to a private file, for example `restricted-issue-helper.local.groovy` (the repository's `.gitignore` excludes `*.local.groovy`), and edit the `CONFIG` block between `// >>> CONFIG` and `// <<< CONFIG`. Every value and every text is described in [CONFIG.md](CONFIG.md). At a minimum:

- `FALLBACK_URL`: your "raise a request" link;
- `RT_FIELD`: the id of your Customer Request Type field (if you run Service Management);
- the internal-viewer policy: `INTERNAL_MAIL_DOMAINS` and `INTERNAL_REQUIRE_USERNAME`, or `INTERNAL_GROUPS` with the `group-policy` profile;
- `SECURED_SCHEMES` and `SECURED_SKIP_LEVELS`: where the restricted card may appear, and where it never may.

Do not edit anything outside the `CONFIG` block of an assembled file. To change the logic, change `src/` and rebuild, so that the change survives the next release instead of having to be found and merged by hand.

With Python, you have a second option: put your values into a profile of your own in `profiles/` as `NAME = value` lines and build it with `python build/assemble.py --profile <name>`. Profiles are not excluded by `.gitignore`, so keep such a profile out of any public fork. The profile format is described in [build/README.md](../build/README.md).

Keep the configured file somewhere private if its values describe your instance; the repository's files hold placeholders only.

## 3. Check and test

With Python:

```
python build/strip_client_comments.py <your file> --check
python build/sync_tests.py --source <your file>
```

The first command must end with `CLEAN`. The second copies the sections of your file, your CONFIG values included, into the three tests. Then run the tests in the Script Console as described in [tests/README.md](../tests/README.md):

1. `tests/render_test.groovy` needs no data and shows every card variant as the browser receives it.
2. `tests/decision_test.groovy` needs a few real accounts and issues that match its scenarios; replace the synthetic ones first, and adjust the expectations your profile changes.
3. `tests/decision_test_secured_off.groovy` proves the kill switch.

All three are read-only.

Without Python: the committed tests hold the sections of `dist/full.groovy` with the placeholder values. `tests/render_test.groovy` still runs as it is and shows every card with the default text. The decision tests are only meaningful with your CONFIG values and your profile's logic, so either run `build/sync_tests.py` once on any machine with Python, or rely on the checks after saving (step 5). The assembled files in `dist/` passed the client-comment check when they were built.

## 4. Create the fragment

In Jira, go to **Administration > ScriptRunner > Fragments** and create a new fragment of the type that ScriptRunner's documentation calls **Show a web panel**:

| Setting | Value | Why |
|---|---|---|
| Note | a name you will recognise, for example "Restricted-issue helper" | shown in the fragments list |
| Location | `atl.header.after.scripts` | included in the head of every decorated page, including the dead-end page of the Service Management agent view, which is stock Jira. Undocumented; on 10.x and 11.x, confirm it with the Fragment Locator |
| Key | any unique key, for example `restricted-issue-helper` | identifies the fragment |
| Weight | `100` | the value the fragment was built and verified with; see [CONFIG.md](CONFIG.md) |
| Condition | none | the fragment decides for itself; on every other page it returns after one regular expression on the request URI |
| Script | the whole configured file, pasted as the panel's inline script | the fragment writes its output through the `writer` that ScriptRunner provides to web panels |

## 5. Check after saving

1. Open an issue you can see: no card, and no `jbh-style` element in the page.
2. Open `/browse/NOPE-99999` (any key that does not exist): the generic card.
3. Open a restricted issue as a test account that should get the restricted card, and as one that should not.
4. View the page source of a card page and confirm the inline script has no comment lines.

## Upgrade

1. Save the currently deployed body to a file: it is your rollback copy.
2. Take the new release's `dist/<profile>.groovy`, or rebuild your profile from the new `src/`, and carry your CONFIG values over. The CONFIG block is the only part you should need to merge; compare it with your previous one, because a release can add a value.
3. Run the checks and the tests (step 3) against the merged file.
4. Replace the fragment's script with the merged file and save.
5. Open the fragment again and compare the saved body with your file (a checksum is enough), so you know the running code is the tested code. Never edit the live body by hand; change your file and redeploy it.

### From 1.0.0

1.0.0 shipped one file, `src/browse-error-helper.groovy`. Its equivalent in 1.1.0 is `dist/full.groovy`: with the same CONFIG values, every viewer sees the same card.

- **Keep the fragment.** Its type, location, weight and condition do not change. 1.0.0's documentation called the type "Custom web panel"; ScriptRunner's documentation calls it "Show a web panel". Only the script changes.
- **Carry your values over one by one**, into the new CONFIG block: `FALLBACK_URL`, `HELP_CENTER`, `MY_REQUESTS`, `RT_FIELD`, `MOVED_CARD`, `SECURED_CARD`, `BOT_NAMES`, `INTERNAL_MAIL_DOMAINS`, `INTERNAL_REQUIRE_USERNAME`, `SECURED_SCHEMES`, `SECURED_SKIP_LEVELS`, and any `TEXT` you reworded.
- **Do not paste your 1.0.0 CONFIG block over the new one.** The block grew: `ESCALATION_BY_PROJECT`, `PAGES`, `MODE_ORDER`, `INTERNAL_GROUPS`, `DISCLOSURE`, `DISCLOSURE_BY_LEVEL` and `SECURED_FIELD_PREFERENCE` are new, and so are the texts `securedLeadFieldUnnamed` and `securedMessageFieldUnnamed`. The code that reads them would fail without them, and because every failure is caught, the fragment would fail quietly. Their defaults reproduce 1.0.0's behaviour; if you translated `TEXT`, translate the two new texts as well.
- **If you changed code in 1.0.0**, it now lives in a module. A group-based `isInternal` is the `group-policy` profile; another policy is a module of its own ([EXTENDING.md](EXTENDING.md)). An `isInternal` check added to the `share` or `moved` card goes into `src/modes/share.groovy` or `src/modes/moved.groovy`, followed by a rebuild.
- **Rerun the decision test's T08, T09, X01 and X02.** The "has application access" gate of the share, moved and secured cards changed from the deprecated `GlobalPermissionKey.USE` to `ApplicationRoleManager.hasAnyRole`. They are meant to agree; these four cases exercise the gate on your accounts.

## Rollback

Four routes, from lightest to strongest:

1. **Say less.** Set `DISCLOSURE` (or one level's entry in `DISCLOSURE_BY_LEVEL`) to withhold the level's name, the field's name or the people, and redeploy. The same viewers get the restricted card, with less on it.
2. **Switch a mode or a page off.** Set `SECURED_CARD` (or `MOVED_CARD`) to `false`, or remove a mode from `MODE_ORDER` or a page from `PAGES`, and redeploy. Every viewer that mode covered gets the generic card again; `tests/decision_test_secured_off.groovy` proves this for the restricted card. A page removed from `PAGES` returns to Jira's stock page.
3. **Redeploy the previous body** you saved before the upgrade. A 1.0.0 body runs as it did before; 1.1.0 stores nothing that it would have to undo.
4. **Disable the fragment** with one toggle in the fragments list. Jira's stock pages return immediately.

## Uninstall

Disable or delete the fragment. It stores nothing: no database rows, no properties, no files.
