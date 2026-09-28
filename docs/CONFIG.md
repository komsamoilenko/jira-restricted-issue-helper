# Configuration reference

Every deployment-specific value lives in the `CONFIG` block of the file you deploy, between `// >>> CONFIG` and `// <<< CONFIG`. Nothing outside that block needs editing in an assembled file. After any change, run `python build/sync_tests.py --source <your file>` so the tests use the same values.

In the source, the block is made of two files: `src/config/config.groovy` (the values) and `src/config/text.en.groovy` (the `TEXT` map). `build/assemble.py` copies both into every file in `dist/`, and applies the profile's overrides on the way.

The shipped defaults are placeholders. Until you replace them, the restricted card never fires (no scheme is in scope and no address is internal) and the "raise a request" links point at a request type that does not exist on your instance.

Every value is present in every build, including values that only one module reads. A value whose module is not in the build is simply never read: `INTERNAL_GROUPS` in the `full` profile, for example.

## Links

| Name | Type | Default | What it does |
|---|---|---|---|
| `FALLBACK_URL` | String | `/servicedesk/customer/portal/1/create/1` | Target of "Raise a request", "Ask for access", "Reporter unavailable? Raise a request" and the other escalation links. Use the create page of the Service Management request type that handles access questions: `/servicedesk/customer/portal/<portal id>/create/<request type id>`. Both ids are in the address bar when you open that request type's form on the portal. Without Service Management, any path on your Jira host works, for example a help page; the `no-jsm` profile uses the stock Contact Administrators form. The generic card always uses this value. |
| `ESCALATION_BY_PROJECT` | Map of String to String | `[:]` | Per-project targets for the escalation links, by project key, for example `['HR': '/servicedesk/customer/portal/2/create/15']`. A project not listed uses `FALLBACK_URL`. Keys are compared exactly, so write them as Jira stores project keys, in capitals. Read only by the `portal`, `share`, `moved` and `secured` cards, which already confirm that the issue exists. The generic card deliberately ignores it and always uses `FALLBACK_URL`: a key that does not exist has no project, so a per-project link on the generic card would tell a hidden issue from a missing key. |
| `HELP_CENTER` | String | `/servicedesk/customer/portals` | Target of "Open the Help Center" on the generic card. The default is the stock Service Management Help Center. |
| `MY_REQUESTS` | String | `/servicedesk/customer/user/requests?status=open` | Target of "See all of my open requests" on the portal card. The default is the stock portal list of the viewer's open requests. |

## Pages and modes

| Name | Type | Default | What it does |
|---|---|---|---|
| `PAGES` | List of String | `['browse', 'agent']` | Which dead-end pages the helper covers. `'browse'` is `/browse/<KEY>`; `'agent'` is the Service Management agent view, `/projects/<P>/queues[/...]/<KEY>` and the queues pages without a key. Remove one to leave that page to Jira: the fragment then returns after matching the URL and writes nothing there. |
| `MODE_ORDER` | List of String | `['portal', 'share', 'moved', 'secured']` | The order in which the modes are tried. The first mode that answers wins; the generic card is the fallback that always exists and is not listed. A name whose module is not in the build is skipped, so the list can stay the same across profiles. Remove a name to switch that mode off. Keep `portal` before `share`: the share card assumes the viewer cannot open the request themselves, which is what `portal` has just ruled out. |

## Service Management

| Name | Type | Default | What it does |
|---|---|---|---|
| `RT_FIELD` | String | `customfield_12345` | Id of the Service Management "Customer Request Type" custom field. An issue in a service-desk project with a value in this field is treated as a request (portal and share cards); outside service desks, a surviving value marks an issue as moved out of one. Find the id under **Administration > Issues > Custom fields**: the number is in the field's configure link. An unknown id is harmless: the portal and share cards stop applying, and moved-issue detection falls back to key history alone. |

## Kill switches

| Name | Type | Default | What it does |
|---|---|---|---|
| `SECURED_CARD` | boolean | `true` | `false` turns the restricted-issue card off: every viewer it would cover gets the generic card instead. `tests/decision_test_secured_off.groovy` proves this. |
| `MOVED_CARD` | boolean | `true` | `false` turns the moved-out-of-the-service-desk card off: those viewers get the generic card. |

Removing the mode from `MODE_ORDER` has the same effect. The switches remain as they were in 1.0.0, and the kill-switch test is built by flipping `SECURED_CARD` in its copy of the CONFIG block.

## Automation accounts

| Name | Type | Default | What it does |
|---|---|---|---|
| `BOT_NAMES` | List of String | `['jira automation', 'jira', 'automation for jira', 'anonymous']` | Display names, compared in lower case, of accounts that are never named as someone who can help on the restricted card and never offered as the reporter to ask on the share card. Add the display names of your own automation, integration and system accounts. The defaults are common display names of stock automation and system accounts; they are a starting point, not a list verified against any instance. |

## Internal-viewer policy

Who may be told that a restricted issue exists. See [DESIGN.md](DESIGN.md#who-counts-as-internal-a-policy-you-choose) for the reasoning. A build holds exactly one policy module: `policy/internal-mail-domain` (profiles `full`, `no-jsm`, `secured-minimal`) or `policy/internal-group` (profile `group-policy`). The `jsm-only` profile has no restricted card and needs no policy. Whatever the policy, the viewer must also have application access (`hasAppAccess`, see DESIGN.md); that part is not configurable.

Read by `policy/internal-mail-domain`:

| Name | Type | Default | What it does |
|---|---|---|---|
| `INTERNAL_MAIL_DOMAINS` | List of String | `['example\\.com']` | Regular expressions, each matched against the whole domain part (after the `@`, lower-cased) of an address. `'example\\.com'` matches `someone@example.com` only; `'example\\.[a-z]+'` matches the same name under any top-level domain; list several entries for several domains. An address must hold exactly one `@`. An empty list means nobody is internal. In Groovy single-quoted strings, write a literal dot as `\\.`. |
| `INTERNAL_REQUIRE_USERNAME` | boolean | `true` | `true`: the username must match `INTERNAL_MAIL_DOMAINS` as well as the e-mail address. Use it where usernames are e-mail addresses that only administrators can change, so a user who edits their own e-mail address still cannot pass. `false`: the e-mail address alone decides; choose it only where users cannot change their own address. If your usernames are not e-mail addresses and you leave this `true`, nobody passes and the restricted card never fires; consider the `group-policy` profile instead. |

Read by `policy/internal-group`:

| Name | Type | Default | What it does |
|---|---|---|---|
| `INTERNAL_GROUPS` | List of String | `['jira-staff']` | Group names. The viewer is internal when they are in at least one of them. Use a group that is maintained to equal staff exactly: groups built for other purposes miss some staff and contain some outsiders. An empty list means nobody is internal. The default is a placeholder; replace it with your group's name. |

## Scope of the restricted card

| Name | Type | Default | What it does |
|---|---|---|---|
| `SECURED_SCHEMES` | List of Long | `[12345L]` | Ids of the issue security schemes whose levels may get the restricted card. Levels of every other scheme keep the generic card. Find ids under **Administration > Issues > Issue security schemes** (the id is in the scheme's edit link). Ids are compared as `Long` whether you write `12345` or `12345L` (1.0.0 needed the `L` suffix; it is still the clearest way to write an id). |
| `SECURED_SKIP_LEVELS` | List of Long | `[12346L, 12347L]` | Ids of levels inside those schemes that must never get the card, because their name or their members are what the level protects (compartments for people matters, for example). Also useful for catch-all levels whose name would confuse. Level ids are in the scheme's level configuration links. Compared as `Long` either way. |
| `SECURED_FIELD_PREFERENCE` | List | `[]` | Which field to suggest when a level grants access through several multi-user picker fields that all qualify. Field ids in order of preference, written as `'customfield_10100'` or as the number alone. The first listed field that qualifies wins; when none of them does, or the list is empty, the lowest field id wins. The list never makes a field qualify: it must still be a multi-user picker granted on the level, apply to the issue's context and be on the issue's edit screen. |
| `RESTRICTED_SCOPE` | String | `'in-scope'` | Scope of the existence-only card (`restricted` mode, off unless listed in `MODE_ORDER`). `'in-scope'`: only levels of `SECURED_SCHEMES` minus `SECURED_SKIP_LEVELS`, so compartments whose existence is the secret stay indistinguishable from missing keys. `'all'`: every issue hidden by a security level. `'any-issue'`: every issue the viewer cannot browse, level or not. New in 1.1.0; see [DESIGN.md](DESIGN.md#existence-only). |

A good way to choose the scope: put a scheme in scope only when most of its levels are team or project compartments whose names are not secret, and list the exceptions. Keep schemes that exist for one sensitive function out of scope entirely.

## What the restricted card may say

These two values run after every gate of the restricted card has passed. They trim what the card says to a viewer who passed; they never change who passes. A withheld value is not sent to the browser at all.

| Name | Type | Default | What it does |
|---|---|---|---|
| `DISCLOSURE` | Map | `[levelName: true, fieldName: true, people: true]` | `levelName: false`: the card says "a security level" (`levelUnnamed`) instead of the level's name. `fieldName: false`: the card still says that someone who can edit the issue can let the viewer in, but without the field's name (`securedLeadFieldUnnamed`, `securedMessageFieldUnnamed`). `people: false`: nobody is named, and the card suggests sending the message to whoever shared the link (`securedLeadNobody`). A key left out of the map counts as `false`. The `secured-minimal` profile sets all three to `false`. |
| `DISCLOSURE_BY_LEVEL` | Map of Long to Map | `[:]` | Per-level overrides, merged over `DISCLOSURE` for that level, by level id with the `L` suffix: `[12348L: [people: false]]`, or `[12349L: [levelName: false, people: false]]`. An override can also switch a value back on for one level. Keep the `L` suffix: with a plain integer key the override is not found, and the level silently gets the global `DISCLOSURE`. |

With every switch off, the card still confirms that the issue exists, that a security level hides it, and whether there is a field through which one more person can be let in. To say none of that, switch the mode off (`SECURED_CARD = false`, or remove `secured` from `MODE_ORDER`) or use the `jsm-only` profile.

## Text

`TEXT` holds every piece of text the card shows. Translate or reword freely. Placeholders in braces are filled in by the card; a placeholder the card does not supply for that string is left as written. Values are sent to the browser as JSON and inserted as plain text, never as HTML. The card joins sentences and greetings with a single space, so values need no leading or trailing spaces, except where noted.

With Python, a profile can reword single entries (`TEXT.genericEscalate = '...'`) or pick a whole text file (`text = config/text.<language>.groovy`); see [build/README.md](../build/README.md). Without Python, edit the `TEXT` map in the `CONFIG` block of your copy.

| Key | Default | Where it appears | Placeholders |
|---|---|---|---|
| `copyLabel` | Copy message | copy buttons | |
| `copied` | Copied to clipboard | copy button after a successful copy | |
| `copyFailed` | Copy it from the box above | copy button when the browser refuses | |
| `greetingNamed` | Hi {first}, | start of a copy-ready message when a first name is usable | `{first}` |
| `greetingPlain` | Hi, | start of a copy-ready message otherwise | |
| `messageHeadingThem` | Message to send them | heading of the message box when people are named | |
| `messageHeading` | Message to send | heading of the message box when nobody is named | |
| `raiseRequest` | Raise a request | generic card, agent view: main button | |
| `openHelpCenter` | Open the Help Center | generic card: Help Center button or link | |
| `openKey` | Open {key} | moved card: link to the new key | `{key}` |
| `roleReporter` | reporter | restricted card: role under a named person | |
| `roleAssignee` | assignee | same | |
| `roleAnd` | and | joins two roles of one person ("reporter and assignee") | |
| `portalTitle` | This request opens on the customer portal | portal card title | |
| `portalText` | You do have access to it there. ... | portal card text on `/browse/` | |
| `portalTextAgent` | The page you opened is the agent view, ... | portal card text on the agent view | |
| `portalOpen` | Open this request | portal card button | |
| `portalMyRequests` | See all of my open requests | portal card link | |
| `movedTitle` | This request moved out of the service desk | moved card title | |
| `movedFrom` | {oldKey} was moved into {project}, as | moved card, before the link to the new key | `{oldKey}`, `{project}` |
| `movedFromUnknown` | It was moved into {project}, as | same, when the former key is unknown | `{project}` |
| `movedAfterLink` | . It is no longer on the customer portal, ... | moved card, directly after the link (starts with its own punctuation, no space is added) | |
| `movedNoAccess` | You do not have access to {project} yet, ... | moved card, second paragraph | `{project}`, `{key}` |
| `movedAsk` | Ask for access | moved card button | |
| `shareTitle` | Ask to be added to this request | share card title | |
| `shareText` | {key} is a Service Management request you are not on. ... | share card text on `/browse/` | `{key}` |
| `shareTextAgent` | {key} is a Service Management request, and this page is the agent view, ... | share card text on the agent view | `{key}` |
| `shareMessage` | I am trying to open {key} but I do not have access to it. ... | share card copy-ready message, after the greeting | `{key}`, `{url}` (portal link), `{mail}` |
| `shareEscalate` | Reporter unavailable? Raise a request | share card link | |
| `securedTitle` | This issue is restricted | restricted card title | |
| `levelNamed` | the security level "{level}" | fills `{levelPhrase}` when the level's name may be shown | `{level}` |
| `levelUnnamed` | a security level | fills `{levelPhrase}` when the level has no name or `DISCLOSURE` withholds it | |
| `securedLead` | {key} is protected by {levelPhrase}, ... | restricted card, first sentence | `{key}`, `{levelPhrase}` |
| `securedLeadField` | You do not have to join them: being added to its "{field}" field ... | restricted card, when there is a field and its name may be shown | `{field}` |
| `securedLeadFieldUnnamed` | You do not have to join them: this issue has a field for letting one more person in, ... | restricted card, when there is a field but `DISCLOSURE` withholds its name. New in 1.1.0 | |
| `securedLeadMany` | Either of the people below can do that in a few seconds. | when two people are named | |
| `securedLeadOne` | The person below can do that in a few seconds. | when one person is named | |
| `securedLeadNobody` | Anyone who can edit the issue can add you, ... | when there is a field but nobody to name, including when `DISCLOSURE` withholds the people | |
| `securedLeadAgent` | Once you are added, open it with the link below rather than from a queue: ... | appended on the agent view when there is a field | |
| `securedLeadNoField` | There is no field on this issue for letting one more person in. ... | restricted card without a field | |
| `securedMessage` | I am trying to open {url}, but it is restricted and I cannot see it. ... | restricted card copy-ready message, after the greeting, when the field's name may be shown | `{url}` (issue link), `{mail}`, `{field}` |
| `securedMessageFieldUnnamed` | I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to the field on the issue that lets one more person see it? ... | same, when `DISCLOSURE` withholds the field's name. New in 1.1.0 | `{url}` (issue link), `{mail}` |
| `securedOpenAgain` | Added already? Open {key} | restricted card link | `{key}` |
| `securedEscalateField` | Nobody to ask? Raise a request | restricted card link, when there is a field | |
| `securedEscalateNoField` | Something else? Raise a request | restricted card link, without a field | |
| `restrictedTitle` | This issue is restricted | existence-only card title (`restricted` mode). New in 1.1.0 | |
| `restrictedText` | {key} exists, but you do not have permission to view it, ... | existence-only card text on `/browse/`. New in 1.1.0 | `{key}` |
| `restrictedTextAgent` | ... Once you have access, open it with the link below rather than from a queue ... | existence-only card text on the agent view. New in 1.1.0 | `{key}` |
| `restrictedOpenAgain` | Got access? Open {key} | existence-only card link. New in 1.1.0 | `{key}` |
| `missingTitle` | No issue with this key | `missing` card title. New in 1.1.0 | |
| `missingText` | There is no issue {key}. It may have been deleted, or the key may be mistyped. ... | `missing` card text. New in 1.1.0 | `{key}` |
| `genericTitle` | You can't view this issue | generic card title on `/browse/` | |
| `genericTitleAgent` | This page is for service desk agents | generic card title on the agent view | |
| `genericText` | It may have been deleted, or you may not have permission. ... | generic card text on `/browse/` | |
| `genericTextAgent` | Queues are the Jira Service Management agent view, ... | generic card text on the agent view | |
| `genericEscalate` | Still stuck? Raise a request and we will help | generic card link on `/browse/` | |

If you run Jira without Service Management, use the `no-jsm` profile: it rewords `openHelpCenter`, `genericText`, `genericEscalate`, `securedEscalateField`, `securedEscalateNoField` and `securedLeadNoField`, and points `HELP_CENTER` and `FALLBACK_URL` at pages that exist without Service Management. Without Python, make the same changes by hand in a copy of any profile.

## What each profile sets

A profile overrides a value with a `NAME = value` line and a text with a `TEXT.key = '...'` line. Everything not listed here keeps the default above.

| Profile | Overrides |
|---|---|
| `full` | none |
| `jsm-only` | `MODE_ORDER = ['portal', 'share', 'moved']` |
| `no-jsm` | `MODE_ORDER = ['secured']`, `FALLBACK_URL = '/secure/ContactAdministrators!default.jspa'` (the Contact Administrators form must be switched on under **Administration > System > General configuration**), `HELP_CENTER = '/secure/Dashboard.jspa'`, and the six texts listed above |
| `secured-minimal` | `DISCLOSURE = [levelName: false, fieldName: false, people: false]` |
| `exists-only` | `MODE_ORDER = ['restricted', 'missing']`, `RESTRICTED_SCOPE = 'all'`; modules: the mail-domain policy and the two existence-only modes only |
| `group-policy` | `INTERNAL_GROUPS = ['jira-staff']`, the same placeholder as the default |

## Fixed values in the code (not configuration)

These are product constants or implementation details rather than site settings. If a future Jira or Service Management release renames one of them, the fragment needs a code change, not a CONFIG change:

- the project type key `service_desk` and the application key `jira-servicedesk`;
- the Service Management API classes, reached by name at run time in `policy/jsm` so that every build still compiles where Service Management is absent;
- the permission keys `ProjectPermissions.BROWSE_PROJECTS` and `ProjectPermissions.EDIT_ISSUES`, the grant type `userCF` and the custom field type key ending in `:multiuserpicker`;
- the URL patterns of the two covered pages, `/browse/<KEY>` and `/projects/<P>/queues[/...]/<KEY>`;
- the DOM hooks `.issue-error` and `#unlicensed-project-type`, and the card's own ids (`jsm-browse-error-helper`, `jbh-style`) and class prefix `jbh-`;
- the wait for the agent view's client-rendered error section: 250 ms polling up to 40 times, plus a DOM observer for 15 s;
- the card's colours, taken from Atlassian's design palette.

## Fragment settings (set in ScriptRunner, not in the script)

| Setting | Value | Notes |
|---|---|---|
| Type | Show a web panel | the name ScriptRunner's documentation uses. 1.0.0's documentation called it "Custom web panel"; it is the same fragment type |
| Location | `atl.header.after.scripts` | rendered in the head of every decorated page, including the agent view's dead-end page. Not documented by Atlassian or Adaptavist; seen in Jira sources from 7.1 to 9.12. On 10.x and 11.x, confirm it with ScriptRunner's Fragment Locator |
| Condition | none | the script decides for itself and returns early on every other page |
| Weight | `100` | Why 100 was chosen is not recorded. The panel writes one inline script and no visible markup of its own, so its order among other header panels should not matter; confirm on your instance if you run other header panels. |
| Key | any unique key | |
| Script | the whole assembled file, CONFIG block configured | pasted inline |
