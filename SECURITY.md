# Security

This fragment runs on every page of a Jira instance and decides, per viewer, how much to say about issues that viewer cannot see. Its job is to be helpful without becoming a disclosure channel. This page states what it never discloses, what each card can reveal, the limitations that are accepted by design, and how to report a problem.

## What the helper never discloses

- **Nothing to anonymous visitors.** Without a logged-in user, no card is computed.
- **Nothing on pages that work.** If the viewer can see the page, the fragment writes nothing, not even a style tag. The same holds on a page type removed from `PAGES`.
- **Nothing about the issue to a viewer who fails the gates.** They get the generic card, whose payload is identical for an issue that exists and is hidden and for a key that does not exist. Its "raise a request" link is always `FALLBACK_URL`, never a per-project link from `ESCALATION_BY_PROJECT`, because a missing key has no project. The existence-only modes (`restricted`, `missing`) relax this on purpose for internal viewers with application access, and only when an administrator lists them in `MODE_ORDER`; they are off in every profile except `exists-only`.
- **Nothing to portal-only customers beyond requests they can already open.** The `share`, `moved` and `secured` cards require application access (`hasAppAccess`: the account holds an application role), so a customer who only uses the portal is never told that an issue they cannot open exists.
- **Never** an issue's summary, description, comments, attachments or field values; never anyone's e-mail address other than the viewer's own (used in their own copy-ready message); never the members of a security level or a group.
- **Never** the name of a level from a scheme outside `SECURED_SCHEMES`, or of a level on `SECURED_SKIP_LEVELS`; and never the existence of an issue on such a level, unless an administrator turns the existence-only modes on at `RESTRICTED_SCOPE` `all` or `any-issue`.
- **Never** more than two people, and only the reporter and the assignee, each only if active, able to see and edit the issue, and not an automation account.
- **Never** a value that `DISCLOSURE` withholds. A withheld level name, field name or list of people is not put into the payload, so it is not in the page source either.
- **No markup injection.** Every server value is JSON-escaped (`<`, `>` and `&` included) before it enters the inline script, and the client inserts it as a text node.
- **No comments in the shipped script.** The assembled files carry none: `build/assemble.py` removes the source comments of the client script and fails the build if `build/strip_client_comments.py --check` finds one.

## What each card can reveal, and to whom

| Card | Shown to | Reveals |
|---|---|---|
| `generic` | any logged-in viewer at a dead end who matches nothing else | nothing about the issue |
| `portal` | a viewer who can already open the request on the customer portal | a link to that request |
| `share` | a viewer with application access who passes the issue's security level | that the request exists, its reporter's display name, its portal link |
| `moved` | a viewer with application access who passes the issue's security level | that the issue exists, its current key, its project's name, its former key |
| `secured` | a viewer with application access who passes the internal-viewer policy and every scope and permission gate | that the issue exists, its security level's name, the field that opens it, up to two people who can add them; each of the last three only if `DISCLOSURE` allows it |
| `restricted` (off unless in `MODE_ORDER`) | a viewer with application access who passes the internal-viewer policy, on an issue within `RESTRICTED_SCOPE` | that the issue exists and the viewer may not see it; nothing else |
| `missing` (off unless in `MODE_ORDER`; answers only at `RESTRICTED_SCOPE` `any-issue` with `restricted` also listed) | a viewer with application access who passes the internal-viewer policy | that no issue has this key. This audience can then tell which keys exist, including by trying keys; at the narrower scopes the mode stays silent so that an issue outside the scope and a missing key look the same |

The gates and their order are set out in [docs/DESIGN.md](docs/DESIGN.md).

## What `DISCLOSURE` does and does not do

`DISCLOSURE` and `DISCLOSURE_BY_LEVEL` decide how much the restricted card says to a viewer who has already passed every gate. They trim content; they never widen the audience. With every switch off (the `secured-minimal` profile), the card still confirms that the issue exists, that a security level hides it, and whether one more person can be let in through a field. If that is too much for a level, list it in `SECURED_SKIP_LEVELS`; if it is too much everywhere, switch the restricted card off (`SECURED_CARD = false`) or run the `jsm-only` profile.

## Dependencies with weaker guarantees

- **`PermissionSchemeManager.hasSchemePermission` is marked `@Internal` by Atlassian.** It is the one internal API the restricted card depends on, for its last permission gate. Its signature is unchanged from Jira 8.0 to 11.x by Javadoc. If a release removes or changes it, the call fails, the `secured` mode gives no answer, and the viewer gets the generic card: the fragment fails towards saying less.
- **`Project.isArchived()` is `@ExperimentalApi`.** A failure there has the same effect.
- **The page location and the DOM hooks are undocumented.** If a Jira release removes `.issue-error` or `#unlicensed-project-type`, the client script finds no node and draws nothing.

[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) lists every API the fragment calls and how each was checked.

## Limitations accepted by design

- **The `share` and `moved` cards are gated on application access, not on the internal-viewer policy.** Any account with an application role that passes the issue's own security level can receive them. If accounts outside your organisation hold application access on your instance and you want those two cards restricted further, add `isInternal(ctx.user)` to the gates in `src/modes/share.groovy` and `src/modes/moved.groovy`, add `isInternal` to their `requires:` lines, and rebuild. The build then needs a policy module: `build/assemble.py` refuses a profile such as `jsm-only` until you add one. See [docs/EXTENDING.md](docs/EXTENDING.md).
- **The restricted card is only as good as your configuration.** The internal-viewer policy, the schemes in scope, the skip list and `DISCLOSURE` are yours to set. Review `SECURED_SKIP_LEVELS` and `DISCLOSURE_BY_LEVEL` whenever a new security level is created in a scheme that is in scope.
- **Display names are shown as stored.** The share card shows the reporter's display name; the restricted card shows the reporter's and assignee's unless `DISCLOSURE` withholds people. Directories that put sensitive information into display names will show it.
- **A mode you add is yours to gate.** Everything a mode puts into its payload reaches the browser. The template in `src/modes/_template.groovy` and [docs/EXTENDING.md](docs/EXTENDING.md) say how to gate before computing content.
- **Browse URLs with `?jql=` or `?filter=`** render a different page and are not covered.

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub's private vulnerability reporting for this repository (**Security > Report a vulnerability**). Do not open a public issue for a suspected disclosure.

A useful report says which version and which profile you run, the viewer's situation (permissions, licence, group or domain as relevant, without real names), the issue's situation (scheme, level, project type), your `DISCLOSURE` settings if the restricted card is involved, which card appeared, and what it revealed that it should not have. Please do not include real names, keys or hostnames from your instance.

Only the latest release receives fixes.
