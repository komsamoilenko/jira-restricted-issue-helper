---
name: Bug report
about: A card appeared where it should not, did not appear where it should, or looked wrong
labels: bug
---

<!--
Suspected disclosure? If a card told someone something it should not have
(that an issue exists, a level's name, a person on the issue), do NOT file it
here. Report it privately: Security > Report a vulnerability. See SECURITY.md.

Please use no real names, issue keys, hostnames, e-mail addresses or ids
from your instance anywhere in this report. Describe roles and situations
instead ("an external contractor with a Jira Software licence").
-->

**Profile and version**
The profile you built from (`full`, `jsm-only`, `no-jsm`, `secured-minimal`, `group-policy`, or your own), and the version in the banner of your file. Did you change any module or the client script?

**Versions**
- Jira Data Center:
- Jira Service Management (or "none"):
- ScriptRunner for Jira:

**Page**
`/browse/<KEY>`, or the Service Management agent view (`/projects/<P>/queues/...`)?

**The viewer's situation** (no real names)
- Application access, or a portal-only customer?
- Internal under your policy (mail domain or group)?
- On the issue's security level? Browse permission on the project?
- Service Management agent licence?

**The issue's situation** (no real keys)
- Project type (software, business, service desk) and whether it is archived:
- Issue security scheme in `SECURED_SCHEMES`? Level on `SECURED_SKIP_LEVELS`?
- A multi-user picker field on the level, and on the edit screen?
- A Service Management request, or moved out of a service desk?

**Which card appeared**
None, `generic`, `portal`, `share`, `moved` or `secured`, and what it said (paraphrase, with names removed).

**What you expected**

**Test output, if any**
The relevant lines from `tests/render_test.groovy` or `tests/decision_test.groovy` (PASS or FAIL, and the `got:` line), with names, keys and hostnames replaced.

**Anything else**
Browser, errors in the browser console, anything unusual about the instance.
