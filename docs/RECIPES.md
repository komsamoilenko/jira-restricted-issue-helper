# Recipes: which profile for which situation

Start from your situation. Each recipe says which profile to take, which CONFIG values to change, what a blocked viewer will see, and which test cases to run. Profiles are in [profiles/](../profiles/), the assembled files in [dist/](../dist/). Every value named here is documented in [CONFIG.md](CONFIG.md), and the reasoning behind the gates is in [DESIGN.md](DESIGN.md).

Test ids refer to [tests/decision_test.groovy](../tests/decision_test.groovy): T01 to T15 cover the cards, X01 to X07 the gates of the restricted card. [tests/README.md](../tests/README.md) says how to run them.

| Situation | Profile | Recipe |
|---|---|---|
| Service Management, issue security, a "can also see" field | `full` | [Service Management and issue security](#service-management-and-issue-security) |
| Jira Software or Jira Core, no Service Management | `no-jsm` | [Without Service Management](#jira-software-or-jira-core-without-service-management) |
| Service Management, no issue security | `jsm-only` | [Without issue security](#service-management-without-issue-security) |
| The card must not name people or the level | `secured-minimal`, or `DISCLOSURE` per level | [Say less](#say-less-no-people-no-level-name) |
| Usernames are logins, not e-mail addresses | `group-policy` | [Usernames](#usernames-that-are-not-e-mail-addresses) |
| A different service desk per department | any, with `ESCALATION_BY_PROJECT` | [A service desk per department](#a-service-desk-per-department) |
| A level grants access through several fields | any with the restricted card | [Several fields](#several-fields-on-one-level) |
| Only `/browse/`, or only the agent view | any, with `PAGES` | [One page only](#only-one-of-the-two-pages) |
| Another language | any, with a `text.<lang>.groovy` file | [Another language](#another-language) |
| Sub-tasks under a restricted parent | any with the restricted card | [Sub-tasks](#sub-tasks-under-a-restricted-parent) |
| Boards, REST, Cloud, Confluence, no access to the project, archived projects | none | [Not covered](#what-the-helper-does-not-cover) |

## Before any recipe

1. Copy `dist/<profile>.groovy` to a private file. A name ending in `.local.groovy` keeps it out of git.
2. Replace the placeholders in its CONFIG block. Until you do, the restricted card never fires and "Raise a request" points at a request type that does not exist.
3. Refresh the tests from your copy, `python build/sync_tests.py --source <your file>`, replace the synthetic accounts and keys in `CASES` with real ones that match each scenario, and run the three tests in the Script Console. Run `render_test` first: it needs no data and shows compilation errors.

A profile can also be changed and rebuilt instead of editing a copy: add `NAME = value` or `TEXT.key = 'value'` lines to it and run `python build/assemble.py --profile <name>`. [EXTENDING.md](EXTENDING.md) explains the profile syntax.

## Service Management and issue security

The author's case. Service Management requests and ordinary issues live side by side, some issues carry security levels, and the levels also grant access through a multi-user picker field on the issue (a "can also see" field).

**Profile:** `full`.

**Change:**

- `FALLBACK_URL`: the create page of the request type that handles access questions.
- `RT_FIELD`: the id of the Customer Request Type field.
- `INTERNAL_MAIL_DOMAINS` and `INTERNAL_REQUIRE_USERNAME`: who counts as internal.
- `SECURED_SCHEMES`: the issue security schemes whose levels may get the restricted card, as Longs (`12345L`).
- `SECURED_SKIP_LEVELS`: levels whose name or members are the secret, as Longs.
- `BOT_NAMES`, if your automation accounts have other display names.
- `HELP_CENTER` and `MY_REQUESTS` only if your portal does not use the stock paths.

**The viewer sees** one of the five cards described in the [README](../README.md): the restricted card with the level's name, the field, up to two people and a copy-ready message; the portal card; the share card; the moved card; or the generic card.

**Test:** every case, T01 to T15 and X01 to X07, the render test, and the kill-switch test.

## Jira Software or Jira Core without Service Management

**Profile:** `no-jsm`. It builds only the restricted card and the generic card, and the profile already sets:

- `MODE_ORDER = ['secured']`;
- `FALLBACK_URL = '/secure/ContactAdministrators!default.jspa'`, the stock Contact Administrators form;
- `HELP_CENTER = '/secure/Dashboard.jspa'`, so the generic card's button leads to the dashboard;
- six texts that stop mentioning the Help Center and requests: `openHelpCenter`, `genericText`, `genericEscalate`, `securedEscalateField`, `securedEscalateNoField`, `securedLeadNoField`.

The Contact Administrators form must be switched on (Administration > System > General configuration). If you cannot switch it on, or you have a page that explains how to ask for access, point `FALLBACK_URL` at that page instead.

**Change:** `INTERNAL_MAIL_DOMAINS`, `INTERNAL_REQUIRE_USERNAME`, `SECURED_SCHEMES`, `SECURED_SKIP_LEVELS`, and `BOT_NAMES` if needed. `RT_FIELD`, `MY_REQUESTS` and `MOVED_CARD` are not used by this build. Without Service Management there is no agent view, so `PAGES = ['browse']` is tidier, but leaving `'agent'` in does no harm.

**The viewer sees** on `/browse/`:

- the generic card: "You can't view this issue", "It may have been deleted, or you may not have permission to view it. If someone sent you this link, ask them for access.", a "Go to the dashboard" button and a "Still stuck? Contact the Jira administrators" link;
- the restricted card, as in `full`, with "Nobody to ask? Contact the Jira administrators" (or "Something else? ..." when there is no field) as its last link.

**Test:** T01, T03 to T06, T14, X01 to X07, the render test and the kill-switch test. T02 and T07 to T13 and T15 concern Service Management; drop them from `CASES`.

## Service Management without issue security

Also for instances that use security levels but do not want the helper to talk about them at all.

**Profile:** `jsm-only`: modules `jsm portal share moved`, `MODE_ORDER = ['portal', 'share', 'moved']`.

**Change:** `FALLBACK_URL`, `RT_FIELD`, `BOT_NAMES`; `HELP_CENTER` and `MY_REQUESTS` if they are not stock; `MOVED_CARD = false` to switch the moved card off. The internal-viewer values, `SECURED_*`, `DISCLOSURE` and `DISCLOSURE_BY_LEVEL` are not read, and no internal-viewer policy is built.

**The viewer sees** the portal, share, moved or generic card. A viewer whom a security level keeps out gets the generic card: the share and moved cards require passing the issue's level.

**Test:** T08 to T15. T07 (a request on a restricted level) now expects `[mode: 'generic']`. T01, T02, T05 and T06 expect the restricted card and do not apply. The X cases can no longer produce a restricted card; keep X01 and X02 as a guard or drop them. The kill-switch test has nothing to switch.

## Say less: no people, no level name

A reader of the post that introduced this project asked how far the card can be configured: "sometimes hiding the assignee or reasoning may be necessary". There are two separate controls:

- **Who gets a restricted card at all** is decided by the gates in [DESIGN.md](DESIGN.md). A viewer who fails any gate gets the generic card, which says nothing about the issue.
- **What the card says to a viewer who passes** is decided by `DISCLOSURE`. It trims content after the gates; it never widens the audience and never hides that the issue exists.

**Profile and values**, from least to most restrictive:

| Need | Do this |
|---|---|
| Hide the people on some levels | `full`, with `DISCLOSURE_BY_LEVEL = [12348L: [people: false]]` |
| Hide the level's name and the people on some levels | `full`, with `DISCLOSURE_BY_LEVEL = [12349L: [levelName: false, people: false]]` |
| Never name people | `full`, with `DISCLOSURE = [levelName: true, fieldName: true, people: false]` |
| Name nothing, anywhere | `secured-minimal`: `DISCLOSURE = [levelName: false, fieldName: false, people: false]` |
| Do not even confirm that issues on a level exist | put the level on `SECURED_SKIP_LEVELS`, or keep its scheme out of `SECURED_SCHEMES` |
| Never talk about restricted issues | `jsm-only`, or `SECURED_CARD = false` |

Level ids take the `L` suffix. A key missing from a `DISCLOSURE` map counts as off. `DISCLOSURE_BY_LEVEL` overrides the global map per level, in both directions.

**The viewer sees**, with everything off: that the issue is protected by a security level, that it has a field for letting one more person in, that being added opens this one issue and nothing else, that anyone who can edit the issue can add them, and a copy-ready message to send to whoever shared the link. The message asks for "the field on the issue that lets one more person see it". With only `people` off, the card keeps the level and the field and says to send the message to whoever shared the link. A withheld value is never computed into the payload, so it is not in the page source either.

**Test:** in the render test, read "secured nobody", "secured field unnamed (DISCLOSURE fieldName false), one person" and "secured no field, unnamed level / agent". In the decision test, for `secured-minimal`, change the expectations of T01, T02, T05 and T07 to `levelName: ''`, `fieldName: null`, `people: []`, and add `hasField: true` where a field exists. For a per-level setting, add a case on an issue at that level. X01 to X07 do not change: `DISCLOSURE` does not touch the gates.

## Usernames that are not e-mail addresses

The shipped mail-domain policy, with `INTERNAL_REQUIRE_USERNAME = true`, needs the username to match the domain patterns too. Where usernames are directory logins, nobody passes and the restricted card never fires. Setting `INTERNAL_REQUIRE_USERNAME = false` is safe only where users cannot change their own e-mail address.

**Profile:** `group-policy`. "Internal" means membership of at least one group in `INTERNAL_GROUPS`.

**Change:** `INTERNAL_GROUPS` (the profile's `'jira-staff'` is a placeholder). Use a group that is maintained to equal staff exactly. A licence group such as the one that grants Jira Software access usually also holds contractor, service and test accounts. The other values are as for `full`; `INTERNAL_MAIL_DOMAINS` and `INTERNAL_REQUIRE_USERNAME` are not read.

If you trust neither signal alone, a policy that requires both a group and a mail domain is a few lines; [EXTENDING.md](EXTENDING.md) has a sketch.

**The viewer sees** the same cards as with `full`. Copy-ready messages carry the viewer's e-mail address, or their username when they have none.

**Test:** as for `full`. X01 (external account) and X02 (service account) must stay generic, so make sure neither account is in the group. X03 is about username against e-mail and does not apply; replace it with "account outside `INTERNAL_GROUPS` whose e-mail is in your domain", expecting `notMode: 'secured'`.

## A service desk per department

**Profile:** any.

**Change:** `ESCALATION_BY_PROJECT`, keyed by the issue's project key:

```groovy
final Map    ESCALATION_BY_PROJECT = ['OPS': '/servicedesk/customer/portal/7/create/31',
                                      'DEMO': '/servicedesk/customer/portal/9/create/40']
```

A project that is not listed uses `FALLBACK_URL`.

**The viewer sees** the project's desk behind "Raise a request", "Ask for access" and the other escalation links of the share, moved and restricted cards. The portal card carries the value but shows no escalation link.

The generic card always keeps `FALLBACK_URL`. A key that does not exist has no project, so a per-project link on the generic card would let a viewer tell a hidden issue in `OPS` from a missing key. Point `FALLBACK_URL` at a desk that can route a request to the right team.

**Test:** add `fallbackUrl: '<the project's URL>'` to the expected map of T01, T12 and T13 when their issues are in a listed project. T14, and any generic case on an issue in a listed project (X05, X06), must still carry `fallbackUrl: FALLBACK_URL`; `CASES` can use the name, because the CONFIG block is copied above it.

## Several fields on one level

By default, among the fields that qualify (a multi-user picker the level grants on, in context for the issue, and on its edit screen), the lowest field id wins.

**Profile:** any with the restricted card.

**Change:** `SECURED_FIELD_PREFERENCE`, in order, as field ids or numbers: `['customfield_10100', 10200]`. The first preferred field that qualifies wins; if none qualifies, the lowest id does. A preference cannot make a field qualify.

One interaction to know: when the permission scheme alone would not let the viewer in, the card still appears if the scheme grants Browse through the chosen field (gate 8 in [DESIGN.md](DESIGN.md)). If only one of the fields is in the permission scheme, prefer that one; preferring another can switch the card off for such viewers.

**The viewer sees** the preferred field's name in the lead and in the copy-ready message.

**Test:** set T01's expected `fieldName` to the preferred field's name. T06 does not change.

## Only one of the two pages

**Profile:** any.

**Change:** `PAGES = ['browse']` for `/browse/<KEY>` only, or `PAGES = ['agent']` for the Service Management agent view only.

**The viewer sees** the card on the listed page, and Jira's stock page on the other.

**Test:** the decision tests call `decide()` directly and do not read `PAGES`. Check in a browser: open the unlisted page as a viewer who hits the dead end. The stock page stays, and the page source holds nothing from the fragment.

## Another language

**Profile:** any.

**Change:**

1. Copy `src/config/text.en.groovy` to `src/config/text.<lang>.groovy`, for example `text.de.groovy`, and save it as UTF-8.
2. Translate the values. Keep every key, and keep every `{placeholder}` spelled exactly as it is.
3. In a profile, set `text = config/text.de.groovy`. `TEXT.key` lines in the same profile apply to that file.
4. Rebuild: `python build/assemble.py --profile <name>`.

Without Python, translate the `TEXT` map in the CONFIG block of your copy instead.

Things to know:

- One build carries one language. The card does not follow the viewer's Jira language.
- The card joins sentences and the greeting with a single space.
- The greeting uses a first name only when it is written in Latin letters (Latin-1 and Latin Extended included); other names get the plain greeting and a "?" avatar.
- The whole `TEXT` map is sent to the browser with every card. Put nothing in it that a viewer may not read.

**Test:** the render test prints every card with your text. Read each variant, on both pages.

## Sub-tasks under a restricted parent

Jira gives a sub-task its parent's security level. It does not copy the value of the "can also see" field: a person added to the parent's field is not on the sub-task's field.

**Profile:** any with the restricted card. No values to change.

**The viewer sees**, on the sub-task's page, a card computed for the sub-task: its own field, its own reporter and assignee, and a message that asks for this sub-task only. "Opens this one issue to you and nothing else" holds, because the field value opens only the issue it is on. If the field is not on the sub-task's edit screen, the card says there is no field, as in T06; add the field to that screen if you want the card to suggest it.

**Test:** add a case for a sub-task under a restricted parent, expecting `[mode: 'secured', fieldName: '<your field>']` and the sub-task's people.

## What the helper does not cover

- **Boards and backlogs** (`RapidBoard.jspa?...selectedIssue=`). An issue opened from a board is a different page, and the fragment does not match it.
- **The REST API and the mobile app.** No web page is rendered, so there is nothing for a web panel to draw into.
- **Browse URLs with `?jql=` or `?filter=`.** They render a different page, which the fragment does not cover.
- **No Browse permission on the project at all.** The viewer gets the generic card, unless the issue's level and the permission scheme meet every gate of the restricted card. A `project-access` mode, naming someone who can grant access to the project under gates like the restricted card's, is planned. Atlassian's suggestion for a request-access button on Data Center, [JRASERVER-59366](https://jira.atlassian.com/browse/JRASERVER-59366), has been open since 2016.
- **Archived projects.** The generic card, tested by X06. An `archived` mode is planned.
- **Deleted issues and keys that never existed.** The generic card, identical to the card for a hidden issue. This is deliberate and will not change.
- **Portal-side reasons** that the portal and share cards do not explain: a request without a request type, a level that excludes the portal's customers, an inactive or duplicate account.
- **Jira Cloud.** Not possible: the fragment is a ScriptRunner for Jira Data Center script that calls the Data Center Java API. Jira Cloud has its own Request access button on a work item you cannot view.
- **Confluence.** Confluence Data Center has its own Request access on restricted pages (Atlassian documentation, "Page restrictions"). Nothing to add.

If your situation is not on this page, open a scenario request (the issue template asks the right questions), or add a mode yourself: [EXTENDING.md](EXTENDING.md).
