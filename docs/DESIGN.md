# Design: the disclosure model

The card exists to be helpful on a page where Jira says nothing useful. Every useful thing it can say about a restricted issue is also a disclosure: that the issue exists, what protects it, and who is involved. This document sets out what the card may say, to whom, and in what order the decisions are taken.

## Principles

1. **Decide on the server, render in the browser.** All permission logic runs in the fragment, per viewer and per issue. The browser receives a small JSON payload (only the values its card needs) and a script that draws it. Nothing is inferred client-side.
2. **Default to the generic card.** The payload is set to the generic card before any lookup. Any exception, any failed gate, any unknown state leaves that card in place. The helper never blanks a page and never breaks one: the entry point is wrapped so that a failure renders nothing at all.
3. **No side channel between hidden and absent.** A viewer who fails the gates gets exactly the same payload for an issue that exists and is hidden as for a key that does not exist.
4. **Say only what the viewer can act on.** The restricted card suggests a field only if being added to it would actually open the issue, and names only people who can actually add the viewer.
5. **Text, never markup.** Server values are JSON-escaped (`<`, `>` and `&` included) before they enter the inline script, and the client inserts them as text nodes. Level names, field names and display names cannot inject markup.
6. **Ship no comments to the browser.** See [build/README.md](../build/README.md).

## What each card may say, and to whom

| Card | Audience | What it reveals |
|---|---|---|
| none | anonymous visitors; viewers who can already see the page | nothing |
| `generic` | every logged-in viewer who reaches a dead end and matches nothing below | nothing about the issue; the same card whether it exists or not |
| `portal` | a viewer who can open the request on the customer portal, checked in the customer context the portal itself uses | a link to a request the viewer can already open |
| `share` | a viewer holding the global USE permission, who passes the issue's security level (or there is none), on a request whose reporter is active, is not the viewer, has an e-mail address, is not an automation account, and has access to the project's portal | that the request exists, the reporter's display name, the portal link. Never the reporter's e-mail address |
| `moved` | a viewer holding USE who passes the issue's security level, on an issue that used to be a service-desk request | that the issue exists, its current key and project name, its former key |
| `secured` | a viewer who passes every gate below | that the issue exists, the level's name, the name of the field that opens it, at most two people (reporter, assignee) who can add the viewer |

The `share` and `moved` cards are gated on USE alone, not on the internal-viewer policy. They reveal less (no level, no compartment), and they are the right answer for anyone with application access who passes the issue's own security. If accounts outside your organisation hold application access and you want those cards restricted further, apply the same `isInternal` check there; see [SECURITY.md](../SECURITY.md).

## Order of decisions

For each request, `decide()` runs in this order and stops at the first answer:

1. **Is the page actually broken for this viewer?** On `/browse/`, the test is Browse Projects on the issue. On the agent view, the page is gated on the Service Management agent licence first, so the test is "holds the agent licence and, when the URL names an issue, can browse it". If the page works, the fragment renders nothing.
2. **Generic card as the default**, before any further lookup.
3. **Service Management request?** Only in a project of type `service_desk` with a request type set. Issues moved out of a service desk keep the request-type field, and the portal lookup throws for them, so the project type is the gate.
   - Viewer can open it on the portal: `portal`.
   - Otherwise, if the viewer holds USE, passes the issue's security level, and the reporter is usable and has access to the portal: `share`.
4. **Moved out of a service desk?** Key history shows a former key in a service-desk project, or the request-type field survived the move. If the viewer holds USE and passes the issue's security level: `moved`.
5. **Hidden by the security level?** Only when the card so far is still generic, and only when every gate of the restricted card holds: `secured`.

## The restricted card's gates, in order

Cheapest first; the first failure returns the generic card.

1. **The issue has a security level.**
2. **The viewer is internal**, under the configured policy (below).
3. **The viewer holds the global USE permission.**
4. **The level is in scope**: its scheme is listed in `SECURED_SCHEMES` and the level is not in `SECURED_SKIP_LEVELS`.
5. **The viewer is not already on the level.** Otherwise the level is not what blocks them.
6. **The project exists, is not archived, and the issue is editable** in its current workflow step. If nobody can fill a field in, there is no honest advice to give.
7. **Find the field to suggest.** Among the level's grants, keep only multi-user picker custom fields that apply to this issue's context and are on the issue's edit screen. The screen is walked tab by tab: the renderer's per-field lookup answers even for fields that are not on the screen. If several qualify, the lowest field id wins. No field is a valid outcome: the card then says there is no field for letting one more person in.
8. **Would being added really let the viewer in?** Issue security is a second lock on top of the permission scheme. The test asks the permission scheme alone about this issue: `PermissionSchemeManager.hasSchemePermission(BROWSE, issue, user, false)` evaluates the scheme's grants for this issue and viewer without the issue-security check that `PermissionManager` adds on top (its last argument is `issueCreation`, false for an existing issue). Failing that, it accepts a scheme that grants Browse through the very field the card suggests. Project-level Browse is not the test: on projects that grant Browse through the reporter or a user field, it is true for everyone.
9. **Who can add them.** The reporter first, then the assignee, each only if active, not the viewer, with an e-mail address, not an automation account (`BOT_NAMES`), and holding Browse and Edit on the issue with the issue editable for them. People are named only when there is a field to fill in.

## Why naming who can help is itself a disclosure

A helpful card and a leaking card differ only in who reads them.

- **Existence.** "This issue is restricted" confirms that the key exists. The generic card deliberately does not.
- **The level's name.** On most levels, a name like "Project team only" is harmless. On some, the name is the compartment: a level created for one personnel matter, one investigation, one negotiation. Telling a viewer the level's name tells them what the issue is about.
- **The people.** The reporter and assignee of a restricted issue are part of what the level protects. On a compartment for people matters, the names on the ticket can be the most sensitive fact of all.
- **The per-viewer computation.** Because the card is computed for each viewer, a wrong audience does not get a vague hint; it gets the real level name and the real people.

That is why the audience gates run before any content is computed, why compartments whose name or members are the secret are excluded by an explicit list rather than by a clever rule, and why a single-user picker is never suggested: those fields usually hold a role (a reviewer, a manager), and asking to be put there means asking to replace a person.

## Who counts as internal: a policy you configure

The restricted card must reach only people inside the organisation. No single signal in Jira proves that:

- **Application access (USE)** is typically also held by contractor, service and test accounts.
- **A group** rarely equals "staff" exactly: groups built for other purposes miss some staff and contain some outsiders.
- **An e-mail address** proves nothing on its own if users can change their own address.

The shipped policy combines signals, and every part of it is configuration:

- the viewer must hold USE, always;
- the viewer's e-mail domain must match one of `INTERNAL_MAIL_DOMAINS` (regular expressions matched against the whole domain);
- with `INTERNAL_REQUIRE_USERNAME = true` (the default), the username must match as well. Use this where usernames are e-mail addresses that only administrators can change: a user who edits their own e-mail address still cannot pass. Set it to `false` only where users cannot change their own e-mail address.

The policy fails closed. An empty domain list, or a username requirement your directory cannot meet, means nobody is internal and the restricted card never fires; everyone keeps the generic card.

If your organisation has a signal it trusts more, such as a group that is maintained to equal staff exactly, change `isInternal` in the DECIDE block; everything else keeps working. Whatever you choose, test it with the decision test's X cases: an external account holding USE, a service account, and an account whose e-mail matches but whose username does not.

## The client script

The script sent to the browser is deliberately small and comment-free:

- it runs once per page, and draws only into Jira's own error block (`.issue-error` on `/browse/`, `#unlicensed-project-type` on the agent view), hiding the stock children. On a page that renders normally neither node exists, so the card cannot appear where it is not wanted, even if the server were wrong;
- the agent view is rendered client-side, so the script polls for about ten seconds and watches DOM mutations for fifteen;
- all text comes from the `TEXT` map in CONFIG; `{placeholders}` are filled in a single pass, so a server value is never re-read as a template;
- the greeting uses a first name only when the first word needed no cleaning, has at least three Latin letters (Latin-1 and Latin Extended included) and is not all caps; otherwise it says a plain "Hi,". Names in other scripts get the plain greeting and a "?" avatar;
- focus rings are drawn with `box-shadow`, because Jira's global CSS removes outlines; the copy button falls back from the Clipboard API to `execCommand`, and then to "copy it from the box above".

## Limitations accepted by design

- Browse URLs with `?jql=` or `?filter=` render a different page, which the fragment does not touch.
- If a level in scope grants access to a group that almost everyone is in, viewers outside that group get a restricted card naming it. Put such catch-all levels on `SECURED_SKIP_LEVELS` if the wording would confuse.
- The `share` and `moved` cards use USE as their audience gate (see above).
