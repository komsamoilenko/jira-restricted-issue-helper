# Jira Restricted Issue Helper

A ScriptRunner web fragment for Jira Data Center that replaces the "You can't view this issue" dead end with a card that says what is actually blocking the viewer and who can let them in, and that decides, per viewer, how much it is allowed to say.

![Before: the stock dead end. After: the restricted-issue card.](docs/img/05-before-after.png)

*All screenshots in this repository use invented data.*

## The problem

When an issue carries an issue security level the viewer is not on, Jira answers: "It may have been deleted or you don't have permission to view it." That sentence sends people in the wrong direction. They ask a Jira administrator, and an administrator does not bypass issue security either, so two people are now blocked instead of one.

The real fix is usually small. Many security levels also grant access through a multi-user picker field on the issue itself (a "can also see" field). Anyone who can edit the issue can add one more name, and that opens this one issue to that one person and nothing else. The blocked viewer has no way to know that, or whom to ask. The card tells them, with a copy-ready message.

## What the card does

The fragment renders nothing on pages that work. On the two dead-end pages, `/browse/<KEY>` and the Jira Service Management agent view, it replaces the error block with one card, in one of five modes:

| Mode | When | What the viewer gets |
|---|---|---|
| `secured` | the issue's security level is what hides it, and the viewer passes every disclosure gate | the level's name, the field that opens this one issue, up to two people who can add them, a copy-ready message |
| `portal` | a Service Management request the viewer can open on the customer portal | a button straight to the request on the portal |
| `share` | a request the viewer is not on, whose reporter can add them with the portal Share button | the reporter's display name and a copy-ready message |
| `moved` | a former request that was moved out of the service desk | its new key and project, instead of a Help Center that will never list it |
| `generic` | anything else, including every viewer who fails a gate | the Help Center and a link to raise a request |

| Stock Jira | Restricted, two people can help |
|---|---|
| ![Stock dead end](docs/img/01-before-jira-default.png) | ![Restricted card naming two people](docs/img/02-after-restricted-two-people.png) |

| Restricted, nobody to name | Restricted, no field to suggest |
|---|---|
| ![Restricted card with nobody named](docs/img/03-after-restricted-nobody-to-name.png) | ![Restricted card without a field](docs/img/04-after-restricted-no-field.png) |

## The hard part: saying who can help is a disclosure

A card that names a security level, a field and the people on an issue tells the viewer that the issue exists, what kind of compartment it sits in, and who is involved. On some levels, the name and the members are exactly what the level protects. So every decision is made on the server, before anything is sent, and the restricted card appears only when all of these hold:

1. the viewer is verifiably internal, under a policy you configure;
2. the level is in a scheme you opted in, and not on your list of compartments whose name is the secret;
3. the issue is editable right now, in a project that is not archived;
4. being added to the field would really let the viewer in: the permission scheme, with issue security left out, already grants them Browse on this issue, or grants it through that very field.

A viewer who fails any gate keeps the generic card, which reads the same whether the issue exists or not. [docs/DESIGN.md](docs/DESIGN.md) sets out the full disclosure model.

## Requirements

- Jira Data Center 9.x
- ScriptRunner for Jira 8.x, with Script Fragments (the fragment is a Custom web panel). The exact ScriptRunner version the fragment was developed against was not recorded, so treat the 8.x range as assumed rather than tested.
- Jira Service Management is optional. The `portal`, `share` and `moved` modes concern Service Management requests (current ones, and issues that used to be requests); the `secured` and `generic` cards work on every project.

The script fragment mechanism this relies on exists on Data Center. On Cloud, the equivalent would need a different approach.

## Quick start

1. Set your values in the `CONFIG` block at the top of [src/browse-error-helper.groovy](src/browse-error-helper.groovy). Every value is documented in [docs/CONFIG.md](docs/CONFIG.md); the defaults are placeholders and keep the restricted card switched off until you replace them.
2. Check the client script: `python build/strip_client_comments.py src/browse-error-helper.groovy --check`
3. Refresh and run the tests: `python build/sync_tests.py`, then paste each file in [tests/](tests/) into the ScriptRunner Script Console (see [tests/README.md](tests/README.md)).
4. Create the fragment: location `atl.header.after.scripts`, weight 100, no condition. Step by step, with upgrade and rollback: [docs/INSTALL.md](docs/INSTALL.md).

## Why the client script carries no comments

Everything inside `writer.write($/ ... /$)` is sent to every browser that receives a card: open the page source and it is there, comments included. Server-side Groovy comments never leave the server. This is not theoretical. In an earlier internal version, two developer comments in the client script, one holding a real display name and one an internal issue key, reached every browser that got a card. Reviews had covered the card's text, the server logic and the client's behaviour; the comments rode along in the bytes.

So in this repository the client script has no comments at all. What it does is explained in server-side comments just above it and in [docs/DESIGN.md](docs/DESIGN.md), and [build/strip_client_comments.py](build/strip_client_comments.py) removes client comments at build time and fails a check if one survives. See [build/README.md](build/README.md).

## Repository layout

```
src/browse-error-helper.groovy        the fragment (CONFIG block at the top)
build/strip_client_comments.py        strips and checks comments in client script blocks
build/sync_tests.py                   copies the deployed sections into the tests
tests/decision_test.groovy            decide() against viewer and issue pairs on your instance
tests/decision_test_secured_off.groovy  the kill switch restores the generic card
tests/render_test.groovy              every card variant, rendered from synthetic payloads
docs/DESIGN.md                        the disclosure model and the gates
docs/INSTALL.md                       install, upgrade, rollback
docs/CONFIG.md                        every configurable value
```

## Security

What the card never discloses, the known limitations, and how to report a vulnerability: [SECURITY.md](SECURITY.md).

## Licence

MIT. See [LICENSE](LICENSE).
