# Configuration reference

Every deployment-specific value lives in the `CONFIG` block at the top of `src/browse-error-helper.groovy`, between `// >>> CONFIG` and `// <<< CONFIG`. Nothing outside that block needs editing. After any change, run `python build/sync_tests.py` so the tests use the same values.

The shipped defaults are placeholders. Until you replace them, the restricted card never fires (no scheme is in scope and no address is internal) and the "raise a request" links point at a request type that does not exist on your instance.

## Links

| Name | Type | Default | What it does |
|---|---|---|---|
| `FALLBACK_URL` | String | `/servicedesk/customer/portal/1/create/1` | Target of "Raise a request", "Ask for access", "Reporter unavailable? Raise a request" and the other escalation links. Use the create page of the Service Management request type that handles access questions: `/servicedesk/customer/portal/<portal id>/create/<request type id>`. Both ids are in the address bar when you open that request type's form on the portal. Without Service Management, any path on your Jira host works, for example a help page. |
| `HELP_CENTER` | String | `/servicedesk/customer/portals` | Target of "Open the Help Center" on the generic card. The default is the stock Service Management Help Center. |
| `MY_REQUESTS` | String | `/servicedesk/customer/user/requests?status=open` | Target of "See all of my open requests" on the portal card. The default is the stock portal list of the viewer's open requests. |

## Service Management

| Name | Type | Default | What it does |
|---|---|---|---|
| `RT_FIELD` | String | `customfield_12345` | Id of the Service Management "Customer Request Type" custom field. An issue in a service-desk project with a value in this field is treated as a request (portal and share cards); outside service desks, a surviving value marks an issue as moved out of one. Find the id under **Administration > Issues > Custom fields**: the number is in the field's configure link. An unknown id is harmless: the portal and share cards stop applying, and moved-issue detection falls back to key history alone. |

## Kill switches

| Name | Type | Default | What it does |
|---|---|---|---|
| `SECURED_CARD` | boolean | `true` | `false` turns the restricted-issue card off: every viewer it would cover gets the generic card instead. `tests/decision_test_secured_off.groovy` proves this. |
| `MOVED_CARD` | boolean | `true` | `false` turns the moved-out-of-the-service-desk card off: those viewers get the generic card. |

## Automation accounts

| Name | Type | Default | What it does |
|---|---|---|---|
| `BOT_NAMES` | List of String | `['jira automation', 'jira', 'automation for jira', 'anonymous']` | Display names, compared in lower case, of accounts that are never named as someone who can help on the restricted card and never offered as the reporter to ask on the share card. Add the display names of your own automation, integration and system accounts. The defaults are common display names of stock automation and system accounts; they are a starting point, not a list verified against any instance. |

## Internal-viewer policy

Who may be told that a restricted issue exists. See [DESIGN.md](DESIGN.md#who-counts-as-internal-a-policy-you-configure) for the reasoning. The viewer must also hold the global USE permission; that part is not configurable.

| Name | Type | Default | What it does |
|---|---|---|---|
| `INTERNAL_MAIL_DOMAINS` | List of String | `['example\\.com']` | Regular expressions, each matched against the whole domain part (after the `@`, lower-cased) of an address. `'example\\.com'` matches `someone@example.com` only; `'example\\.[a-z]+'` matches the same name under any top-level domain; list several entries for several domains. An address must hold exactly one `@`. An empty list means nobody is internal. In Groovy single-quoted strings, write a literal dot as `\\.`. |
| `INTERNAL_REQUIRE_USERNAME` | boolean | `true` | `true`: the username must match `INTERNAL_MAIL_DOMAINS` as well as the e-mail address. Use it where usernames are e-mail addresses that only administrators can change, so a user who edits their own e-mail address still cannot pass. `false`: the e-mail address alone decides; choose it only where users cannot change their own address. If your usernames are not e-mail addresses and you leave this `true`, nobody passes and the restricted card never fires. |

## Scope of the restricted card

| Name | Type | Default | What it does |
|---|---|---|---|
| `SECURED_SCHEMES` | List of Long | `[12345L]` | Ids of the issue security schemes whose levels may get the restricted card. Levels of every other scheme keep the generic card. Find ids under **Administration > Issues > Issue security schemes** (the id is in the scheme's edit link). Keep the `L` suffix: scheme ids are compared as `Long`, and `[12345].contains(12345L)` is false in Groovy, which would silently switch the card off. |
| `SECURED_SKIP_LEVELS` | List of Long | `[12346L, 12347L]` | Ids of levels inside those schemes that must never get the card, because their name or their members are what the level protects (compartments for people matters, for example). Also useful for catch-all levels whose name would confuse. Level ids are in the scheme's level configuration links. Keep the `L` suffix. |

A good way to choose: put a scheme in scope only when most of its levels are team or project compartments whose names are not secret, and list the exceptions. Keep schemes that exist for one sensitive function out of scope entirely.

## Text

`TEXT` holds every piece of text the card shows. Translate or reword freely. Placeholders in braces are filled in by the card; a placeholder the card does not supply for that string is left as written. Values are sent to the browser as JSON and inserted as plain text, never as HTML. The card joins sentences and greetings with a single space, so values need no leading or trailing spaces, except where noted.

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
| `levelNamed` | the security level "{level}" | fills `{levelPhrase}` when the level has a name | `{level}` |
| `levelUnnamed` | a security level | fills `{levelPhrase}` otherwise | |
| `securedLead` | {key} is protected by {levelPhrase}, ... | restricted card, first sentence | `{key}`, `{levelPhrase}` |
| `securedLeadField` | You do not have to join them: being added to its "{field}" field ... | restricted card, when there is a field | `{field}` |
| `securedLeadMany` | Either of the people below can do that in a few seconds. | when two people are named | |
| `securedLeadOne` | The person below can do that in a few seconds. | when one person is named | |
| `securedLeadNobody` | Anyone who can edit the issue can add you, ... | when there is a field but nobody to name | |
| `securedLeadAgent` | Once you are added, open it with the link below rather than from a queue: ... | appended on the agent view when there is a field | |
| `securedLeadNoField` | There is no field on this issue for letting one more person in. ... | restricted card without a field | |
| `securedMessage` | I am trying to open {url}, but it is restricted and I cannot see it. ... | restricted card copy-ready message, after the greeting | `{url}` (issue link), `{mail}`, `{field}` |
| `securedOpenAgain` | Added already? Open {key} | restricted card link | `{key}` |
| `securedEscalateField` | Nobody to ask? Raise a request | restricted card link, when there is a field | |
| `securedEscalateNoField` | Something else? Raise a request | restricted card link, without a field | |
| `genericTitle` | You can't view this issue | generic card title on `/browse/` | |
| `genericTitleAgent` | This page is for service desk agents | generic card title on the agent view | |
| `genericText` | It may have been deleted, or you may not have permission. ... | generic card text on `/browse/` | |
| `genericTextAgent` | Queues are the Jira Service Management agent view, ... | generic card text on the agent view | |
| `genericEscalate` | Still stuck? Raise a request and we will help | generic card link on `/browse/` | |

If you run Jira without Service Management, reword `genericText` and `genericEscalate` (they mention the Help Center and requests), and point `HELP_CENTER` and `FALLBACK_URL` at pages that exist on your instance.

## Fixed values in the code (not configuration)

These are product constants or implementation details rather than site settings. If a future Jira or Service Management release renames one of them, the fragment needs a code change, not a CONFIG change:

- the project type key `service_desk` and the application key `jira-servicedesk`;
- the Service Management API classes, reached reflectively so the fragment still compiles where Service Management is absent;
- the URL patterns of the two covered pages, `/browse/<KEY>` and `/projects/<P>/queues[/...]/<KEY>`;
- the DOM hooks `.issue-error` and `#unlicensed-project-type`, and the card's own ids (`jsm-browse-error-helper`, `jbh-style`) and class prefix `jbh-`;
- the wait for the agent view's client-rendered error section: 250 ms polling up to 40 times, plus a DOM observer for 15 s;
- the card's colours, taken from Atlassian's design palette.

## Fragment settings (set in ScriptRunner, not in the script)

| Setting | Value | Notes |
|---|---|---|
| Type | Custom web panel | |
| Location | `atl.header.after.scripts` | rendered in the head of every decorated page, including the agent view's dead-end page |
| Condition | none | the script decides for itself and returns early on every other page |
| Weight | `100` | Why 100 was chosen is not recorded. The panel writes one inline script and no visible markup of its own, so its order among other header panels should not matter; confirm on your instance if you run other header panels. |
| Key | any unique key | |
