# Security

This fragment runs on every page of a Jira instance and decides, per viewer, how much to say about issues that viewer cannot see. Its job is to be helpful without becoming a disclosure channel. This page states what it never discloses, what each card can reveal, the limitations that are accepted by design, and how to report a problem.

## What the helper never discloses

- **Nothing to anonymous visitors.** Without a logged-in user, no card is computed.
- **Nothing on pages that work.** If the viewer can see the page, the fragment writes nothing, not even a style tag.
- **Nothing about the issue to a viewer who fails the gates.** They get the generic card, whose payload is identical for an issue that exists and is hidden and for a key that does not exist.
- **Never** an issue's summary, description, comments, attachments or field values; never anyone's e-mail address other than the viewer's own (used in their own copy-ready message); never the members of a security level or a group.
- **Never** a level from a scheme outside `SECURED_SCHEMES`, or a level on `SECURED_SKIP_LEVELS`.
- **Never** more than two people, and only the reporter and the assignee, each only if active, able to see and edit the issue, and not an automation account.
- **No markup injection.** Every server value is JSON-escaped (`<`, `>` and `&` included) before it enters the inline script, and the client inserts it as a text node.
- **No comments in the shipped script.** The client script carries none, and `build/strip_client_comments.py --check` fails if one appears.

## What each card can reveal, and to whom

| Card | Shown to | Reveals |
|---|---|---|
| `generic` | any logged-in viewer at a dead end who matches nothing else | nothing about the issue |
| `portal` | a viewer who can already open the request on the customer portal | a link to that request |
| `share` | a viewer holding the global USE permission who passes the issue's security level | that the request exists, its reporter's display name, its portal link |
| `moved` | a viewer holding USE who passes the issue's security level | that the issue exists, its current key, its project's name, its former key |
| `secured` | a viewer who holds USE, passes the internal-viewer policy, and passes every scope and permission gate | that the issue exists, its security level's name, the field that opens it, up to two people who can add them |

The gates and their order are set out in [docs/DESIGN.md](docs/DESIGN.md).

## Limitations accepted by design

- **The `share` and `moved` cards are gated on USE, not on the internal-viewer policy.** Any account with application access that passes the issue's own security level can receive them. If accounts outside your organisation hold application access on your instance and you want those two cards restricted further, add the `isInternal(user)` check to their conditions in the DECIDE block.
- **The restricted card is only as good as your configuration.** The internal-viewer policy, the schemes in scope and the skip list are yours to set. Review `SECURED_SKIP_LEVELS` whenever a new security level is created in a scheme that is in scope.
- **Display names are shown as stored.** The share card shows the reporter's display name; the restricted card shows the reporter's and assignee's. Directories that put sensitive information into display names will show it.
- **Browse URLs with `?jql=` or `?filter=`** render a different page and are not covered.

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub's private vulnerability reporting for this repository (**Security > Report a vulnerability**). Do not open a public issue for a suspected disclosure.

A useful report says which version you run, the viewer's situation (permissions, licence, group or domain as relevant, without real names), the issue's situation (scheme, level, project type), which card appeared, and what it revealed that it should not have. Please do not include real names, keys or hostnames from your instance.

Only the latest release receives fixes.
