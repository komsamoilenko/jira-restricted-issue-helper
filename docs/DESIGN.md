# Design: the disclosure model

The card exists to be helpful on a page where Jira says nothing useful. Every useful thing it can say about a restricted issue is also a disclosure: that the issue exists, what protects it, and who is involved. This document sets out what the card may say, to whom, and in what order the decisions are taken.

## Principles

1. **Decide on the server, render in the browser.** All permission logic runs in the fragment, per viewer and per issue. The browser receives a small JSON payload (only the values its card needs) and a script that draws it. Nothing is inferred client-side.
2. **Default to the generic card.** The payload is set to the generic card before any mode runs. Each mode runs inside its own `try`: an exception, a failed gate or an unknown state means "no answer", and the generic card stays. The helper never blanks a page and never breaks one: the entry point is wrapped so that a failure renders nothing at all.
3. **No side channel between hidden and absent.** A viewer who fails the gates gets exactly the same payload for an issue that exists and is hidden as for a key that does not exist. The one deliberate exception is the pair of existence-only modes, `restricted` and `missing`, which are off unless an administrator puts them in `MODE_ORDER` and which speak only to internal viewers with application access (see "Existence only").
4. **Gates decide who; `DISCLOSURE` decides how much.** Configuration can make the restricted card say less to the viewers who pass its gates. It cannot make the card reach anyone the gates turned away.
5. **Say only what the viewer can act on.** The restricted card suggests a field only if being added to it would actually open the issue, and names only people who can actually add the viewer.
6. **Text, never markup.** Server values are JSON-escaped (`<`, `>` and `&` included) before they enter the inline script, and the client inserts them as text nodes. Level names, field names and display names cannot inject markup.
7. **Ship no comments to the browser.** See [build/README.md](../build/README.md).

## How one page view is handled

The assembled file runs four parts in order. In the source, each is a module under `src/`.

1. **Entry** (`core/entry`). Match the request URI against the two covered pages, and stop unless the page is listed in `PAGES`. Take the logged-in user (none: stop), resolve the issue from the key, and from then on use the issue's own key rather than the URL's: Jira serves lowercase keys, and a moved issue still answers on its old key. The request object is left untyped on purpose (see [Compatibility choices](#compatibility-choices-in-the-code)).
2. **Decide** (`core/decide`). If the page works for this viewer, return nothing. Otherwise set the generic card, then try the modes.
3. **Modes** (`modes/*`). Each mode module adds one entry to a registry, `MODES['<name>'] = { ctx -> payload or null }`. `decide()` walks `MODE_ORDER` and takes the first mode that returns a payload with a `mode`. A name whose module is not in the build is skipped; a mode that throws counts as no answer. If no mode answers, the generic card stands. The generic card is not a module and cannot be switched off: it is the fallback that always exists.
4. **Render** (`render/render`). JSON-escape the payload and the `TEXT` map, and write them with the client script into the page.

Helpers shared by the modes live in `core/context` (`hasAppAccess`, `passesSecurity`, `escalationFor` and a few others), and replaceable decisions live in policy modules: who counts as internal (`isInternal`), whom the restricted card may name (`peopleFor`), and the Service Management lookups (`policy/jsm`). Every module declares what it `provides` and `requires`, and `build/assemble.py` refuses a build whose requirements are not met. [EXTENDING.md](EXTENDING.md) sets out the contracts.

## What each card may say, and to whom

| Card | Audience | What it reveals |
|---|---|---|
| none | anonymous visitors; viewers who can already see the page; pages not listed in `PAGES` | nothing |
| `generic` | every logged-in viewer who reaches a dead end and matches nothing below | nothing about the issue; the same card whether it exists or not |
| `portal` | a viewer who can open the request on the customer portal, checked in the customer context the portal itself uses | a link to a request the viewer can already open |
| `share` | a viewer with application access, who passes the issue's security level (or there is none), on a request whose reporter is active, is not the viewer, has an e-mail address, is not an automation account, and has access to the project's portal | that the request exists, the reporter's display name, the portal link. Never the reporter's e-mail address |
| `moved` | a viewer with application access who passes the issue's security level, on an issue that used to be a service-desk request | that the issue exists, its current key and project name, its former key |
| `secured` | a viewer who passes every gate below | that the issue exists, the level's name, the name of the field that opens it, at most two people (reporter, assignee) who can add the viewer. `DISCLOSURE` can withhold the name, the field's name and the people |
| `restricted` (off by default) | an internal viewer with application access, on an issue hidden from them within `RESTRICTED_SCOPE` | that the issue exists and the viewer may not see it. Nothing else: no level, no field, no people |
| `missing` (off by default) | an internal viewer with application access, on a key that resolves to nothing | that no issue has this key |

The `share` and `moved` cards are gated on application access alone, not on the internal-viewer policy. They reveal less (no level, no compartment), and they are the right answer for anyone with application access who passes the issue's own security. If accounts outside your organisation hold application access and you want those cards restricted further, add the `isInternal` check to those two modes; see [SECURITY.md](../SECURITY.md).

## Application access

`hasAppAccess(user)` is `user.isActive() && ApplicationRoleManager.hasAnyRole(user)`: true for an active account that holds any application role (Jira Software, Jira Core, a Service Management agent seat), false for portal-only customers, deactivated accounts and anonymous. It is the "not a portal-only customer" gate of the `share`, `moved` and `secured` cards, so a customer who only uses the portal is never told that an issue they cannot open exists.

1.0.0 asked `GlobalPermissionKey.USE` for the same thing. That key is marked `@Deprecated` ("Use ApplicationAuthorizationService instead. Since v7.0") in every Javadoc from Jira 8.0 to 11.x; 1.1.0 uses `ApplicationRoleManager.hasAnyRole`, present with the same signature over the same range. `hasAnyRole` alone does not look at the account's status: on one instance it answered true for 296 deactivated accounts where `USE` answered false, which is why `isActive()` stands in front of it; with it, the two agreed for all 2 676 accounts there. The decision test's X08 to X13 make this gate the deciding one on your accounts (an internal viewer without application access, and a deactivated internal account with one, against a restricted issue, a shareable request and a moved issue), with preconditions that prove no other gate stopped the card.

## The two escalation links

Every card except `portal` offers "raise a request" or "ask for access". Two values decide where it leads:

- **The generic card always uses `FALLBACK_URL`.** It is set in `decide()` before any mode runs and never depends on the project. A key that does not exist has no project, so a per-project link on the generic card would let a viewer tell a hidden issue from a missing key.
- **The other cards use `escalationFor(project)`**: the project's entry in `ESCALATION_BY_PROJECT`, or `FALLBACK_URL` when there is none. Those cards already confirm that the issue exists, to a viewer who passed their gates, so a per-project link reveals nothing new.

## Order of decisions

For each request, `decide()` runs in this order and stops at the first answer. Steps 3 to 6 follow the default `MODE_ORDER`, `['portal', 'share', 'moved', 'secured']`, which reproduces 1.0.0.

1. **Is the page actually broken for this viewer?** On `/browse/`, the test is Browse Projects on the issue. On the agent view, the page is gated on the Service Management agent licence first, so the test is "holds the agent licence and, when the URL names an issue, can browse it". If the page works, the fragment renders nothing.
2. **Generic card as the default**, before any mode runs.
3. **`portal`.** Only for a current Service Management request: a project of type `service_desk` and a value in the request-type field. Issues moved out of a service desk keep the request-type field, and the portal lookup throws for them, so the project type is the gate. If the viewer can open the request on the portal, checked in the customer context: `portal`.
4. **`share`.** The same kind of request, which the viewer cannot open. If the viewer has application access, passes the issue's security level, and the reporter is usable and has access to the portal: `share`. The share card assumes that `portal` has already been tried, so keep `portal` before `share`.
5. **`moved`.** Key history shows a former key in a service-desk project, or the request-type field survived the move, and the issue is no longer in a service desk. If the viewer has application access and passes the issue's security level: `moved`. Kill switch `MOVED_CARD`.
6. **`secured`.** Only when every gate of the restricted card holds. Kill switch `SECURED_CARD`.

## The restricted card's gates, in order

Cheapest first; the first failure returns no answer, and the viewer keeps the generic card.

1. **The issue has a security level.**
2. **The viewer is internal**, under the configured policy (below).
3. **The viewer has application access** (`hasAppAccess`).
4. **The level is in scope**: its scheme is listed in `SECURED_SCHEMES` and the level is not in `SECURED_SKIP_LEVELS`.
5. **The viewer is not already on the level.** Otherwise the level is not what blocks them.
6. **The project exists, is not archived, and the issue is editable** in its current workflow step. If nobody can fill a field in, there is no honest advice to give.
7. **Find the field to suggest.** Among the level's grants, keep only multi-user picker custom fields that apply to this issue's context and are on the issue's edit screen. The screen is walked tab by tab: the renderer's per-field lookup answers even for fields that are not on the screen. If several qualify, the first one listed in `SECURED_FIELD_PREFERENCE` wins, and otherwise the lowest field id. No field is a valid outcome: the card then says there is no field for letting one more person in.
8. **Would being added really let the viewer in?** Issue security is a second lock on top of the permission scheme. The test asks the permission scheme alone about this issue: `PermissionSchemeManager.hasSchemePermission(BROWSE, issue, user, false)` evaluates the scheme's grants for this issue and viewer without the issue-security check that `PermissionManager` adds on top (its last argument is `issueCreation`, false for an existing issue). Atlassian's Javadoc does not state that behaviour explicitly, and the method is marked `@Internal`; the fragment relies on it, and the decision test's T01 and X07 cases exercise it on your instance. If the call ever fails, the mode returns no answer and the viewer keeps the generic card, which is the safe direction. Failing that test, the gate accepts a scheme that grants Browse through the very field the card suggests; the project's permission scheme is looked up first, and a project without one fails the gate. Project-level Browse is not the test: on projects that grant Browse through the reporter or a user field, it is true for everyone.
9. **What the card may say.** `DISCLOSURE`, with the level's entry in `DISCLOSURE_BY_LEVEL` merged over it, decides whether the payload carries the level's name, the field's name and the people. See below.
10. **Who can add them.** Only when there is a field and `DISCLOSURE` allows people: the reporter first, then the assignee, each only if active, not the viewer, with an e-mail address, not an automation account (`BOT_NAMES`), and holding Browse and Edit on the issue with the issue editable for them. The shipped `policy/people-reporter-assignee` module makes this choice; another module could name someone else in the same shape.

## What `DISCLOSURE` changes, and what it does not

`DISCLOSURE` runs after every gate has passed. It trims the content of the restricted card; it never widens the audience, and it does not narrow it either: a viewer who passes the gates still gets a restricted card, only with less on it.

| Switch off | Payload | What the card says instead |
|---|---|---|
| `levelName` | `levelName` is empty | "a security level" (`levelUnnamed`) |
| `fieldName` | `fieldName` is null, `hasField` is true | that the issue has a field for letting one more person in, without naming it, and a copy-ready message that asks for "the field on the issue that lets one more person see it" (`securedLeadFieldUnnamed`, `securedMessageFieldUnnamed`) |
| `people` | `people` is empty | nobody named; "send the message below to whoever shared the link with you" (`securedLeadNobody`) |

A withheld value is not computed into the payload, so it never reaches the browser, not even in the page source. A key missing from the map counts as off. `DISCLOSURE_BY_LEVEL` overrides the global map per level id, in both directions.

With all three switches off (the `secured-minimal` profile), the card still confirms that the issue exists, that a security level hides it, and, through `hasField`, whether one more person can be let in through a field. An organisation for which even that is too much should switch the mode off, or run the `jsm-only` profile.

## Existence only

Some administrators do not want the card to say anything about a restricted issue, and still want their staff to tell "restricted" from "deleted or mistyped". Two modes, both off unless listed in `MODE_ORDER`, do exactly that and no more:

- `restricted`: the issue exists and this viewer may not see it. The card says so, offers "raise a request" and a link to open the key once access is granted. No level name, no field, no people, no copy-ready message.
- `missing`: the key resolves to no issue. The card says so.

Both speak only to a viewer who passes the internal-viewer policy and holds application access; everyone else keeps the generic card, which reads the same either way. `RESTRICTED_SCOPE` sets how far `restricted` goes: `in-scope` (the default) confirms only issues whose level is in `SECURED_SCHEMES` and not in `SECURED_SKIP_LEVELS`; `all` confirms every issue hidden by a level; `any-issue` confirms every issue the viewer cannot browse, level or not. An unknown value keeps the mode silent.

The scope cannot hide existence from the audience unless the `missing` card stays silent too. Measured on one instance with both modes on: at `in-scope` a compartment issue got the generic card while a missing key got `missing`, so the audience could still tell them apart; the scope changed the wording, not the fact. Hence the rule in the code: `missing` answers only at `any-issue` with `restricted` also in `MODE_ORDER`. At `in-scope` and `all`, a missing key and an issue outside the scope both get the generic card, and the invariant holds: what the scope leaves out stays indistinguishable from a key that does not exist.

The trade-off at `any-issue` is stated plainly: the audience can tell which keys exist, including by trying keys. That is acceptable for staff on many instances and never for customers, which is why the audience gate is not configurable downwards and why the modes are off by default. The `exists-only` profile builds just these two modes at `any-issue`; `full` builds them in but leaves them out of `MODE_ORDER`, so an administrator can add `restricted` after `secured` and get the detailed card where it is safe and at least the fact everywhere else in scope, with no `missing` card.

Two details of `restricted`: it repeats the key the viewer used, not the canonical key, so an issue reached through an old key after a move does not reveal its new project (the `moved` card does that on purpose, under its own gates); and it has no "project not archived" gate, because it gives no advice that would need an editable issue, so an archived issue the viewer cannot browse is confirmed to exist like any other.

## Why naming who can help is itself a disclosure

A helpful card and a leaking card differ only in who reads them.

- **Existence.** "This issue is restricted" confirms that the key exists. The generic card deliberately does not.
- **The level's name.** On most levels, a name like "Project team only" is harmless. On some, the name is the compartment: a level created for one personnel matter, one investigation, one negotiation. Telling a viewer the level's name tells them what the issue is about.
- **The people.** The reporter and assignee of a restricted issue are part of what the level protects. On a compartment for people matters, the names on the ticket can be the most sensitive fact of all.
- **The per-viewer computation.** Because the card is computed for each viewer, a wrong audience does not get a vague hint; it gets the real level name and the real people.

That is why the audience gates run before any content is computed, why compartments whose name or members are the secret are excluded by an explicit list rather than by a clever rule, why an organisation can withhold names and people with `DISCLOSURE` globally or per level, and why a single-user picker is never suggested: those fields usually hold a role (a reviewer, a manager), and asking to be put there means asking to replace a person.

## Who counts as internal: a policy you choose

The restricted card must reach only people inside the organisation. No single signal in Jira proves that:

- **Application access** is typically also held by contractor, service and test accounts.
- **A group** rarely equals "staff" exactly: groups built for other purposes miss some staff and contain some outsiders.
- **An e-mail address** proves nothing on its own if users can change their own address.

So the restricted card always requires application access and, on top of it, one policy module that provides `isInternal`. Two are shipped; a build holds exactly one:

- **`policy/internal-mail-domain`** (profiles `full`, `no-jsm`, `secured-minimal`). The viewer's e-mail domain must match one of `INTERNAL_MAIL_DOMAINS` (regular expressions matched against the whole domain). With `INTERNAL_REQUIRE_USERNAME = true` (the default), the username must match as well. Use this where usernames are e-mail addresses that only administrators can change: a user who edits their own e-mail address still cannot pass. Set it to `false` only where users cannot change their own e-mail address.
- **`policy/internal-group`** (profile `group-policy`). The viewer must be in one of `INTERNAL_GROUPS`. Use it where usernames are logins rather than e-mail addresses, and only with a group that is maintained to equal staff exactly.

Both policies fail closed. An empty domain or group list, or a username requirement your directory cannot meet, means nobody is internal and the restricted card never fires; everyone keeps the generic card.

If your organisation has a signal it trusts more, write a module that provides `isInternal` and build it instead ([EXTENDING.md](EXTENDING.md)); everything else keeps working. Whatever you choose, test it with the decision test's X cases: an external account with application access, a service account, and an account whose e-mail matches but whose username does not (with the group policy: accounts outside the groups).

## The client script

The script sent to the browser is deliberately small and comment-free. Its source, `src/render/client.js`, explains itself in full-line comments that the build removes (see [build/README.md](../build/README.md)).

- It runs once per page, and draws only into Jira's own error block (`.issue-error` on `/browse/`, `#unlicensed-project-type` on the agent view), hiding the stock children. On a page that renders normally neither node exists, so the card cannot appear where it is not wanted, even if the server were wrong.
- The agent view is rendered client-side, so the script polls for about ten seconds and watches DOM mutations for fifteen.
- All text comes from the `TEXT` map in CONFIG; `{placeholders}` are filled in a single pass, so a server value is never re-read as a template.
- On the restricted card, the field sentence and the copy-ready message take the named variant when the payload holds a field name, and the unnamed variant when it holds only `hasField`. Without either, the card says there is no field.
- The greeting uses a first name only when the first word needed no cleaning, has at least three Latin letters (Latin-1 and Latin Extended included) and is not all caps; otherwise it says a plain "Hi,". Names in other scripts get the plain greeting and a "?" avatar.
- Focus rings are drawn with `box-shadow`, because Jira's global CSS removes outlines; the copy button falls back from the Clipboard API to `execCommand`, and then to "copy it from the box above".

## Compatibility choices in the code

Each of these keeps the fragment on documented, stable ground where it can, and makes the failure safe where it cannot. [COMPATIBILITY.md](COMPATIBILITY.md) has the version-by-version detail and the sources.

- **Application access** is `isActive()` plus `ApplicationRoleManager.hasAnyRole`, not the deprecated `GlobalPermissionKey.USE` (see above).
- **`getUsersSecurityLevels`** is documented as possibly returning null; `passesSecurity` treats null as "no levels".
- **`getSchemeFor`** may return null; the field route of gate 8 checks it and fails the gate.
- **`PermissionSchemeManager.hasSchemePermission`** is the one `@Internal` API the restricted card depends on. It is kept, in one call inside the `secured` mode; if it fails, the mode gives no answer and the viewer gets the generic card.
- **`Project.isArchived()`** is `@ExperimentalApi` and first appears in Jira 7.9.0. A failure there, too, means the generic card.
- **The Service Management callable** that runs a check in the customer context is made with Groovy's `asType`, not a bare `java.lang.reflect.Proxy`, so it answers `equals`, `hashCode` and `toString` properly. The Service Management classes are reached by name at run time, so every build compiles where Service Management is absent. That a fragment script can reach them this way is confirmed on the author's instance, not by documentation.
- **Permission keys** are the `ProjectPermissions.BROWSE_PROJECTS` and `ProjectPermissions.EDIT_ISSUES` constants, not string literals.
- **The request object stays untyped.** On Jira 11, `ExecutingHttpRequest.get()` returns a `jakarta.servlet` request, on earlier versions a `javax.servlet` one. Naming either type would break the other.

## Limitations accepted by design

- Browse URLs with `?jql=` or `?filter=` render a different page, which the fragment does not touch; so do boards and backlogs, the REST API and the mobile app.
- A viewer who lacks Browse on an ordinary Jira project gets the generic card; the card does not name project leads or administrators.
- If a level in scope grants access to a group that almost everyone is in, viewers outside that group get a restricted card naming it. Put such catch-all levels on `SECURED_SKIP_LEVELS` if the wording would confuse.
- The `share` and `moved` cards use application access as their audience gate (see above).
- The location `atl.header.after.scripts` and the DOM hooks `.issue-error` and `#unlicensed-project-type` are not documented by Atlassian. The location is seen in Jira sources from 7.1 to 9.12, and the hooks are confirmed on the author's production instance (Jira 9.x, ScriptRunner 8.x). If a Jira release removes a hook, the script finds no node and draws nothing, and the stock page stays.
