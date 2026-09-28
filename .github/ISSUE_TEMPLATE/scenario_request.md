---
name: Scenario request
about: A dead end the helper does not cover yet, or covers with the wrong card
labels: scenario
---

<!--
Please use no real names, issue keys, hostnames or ids from your instance.
Describe roles and situations instead. docs/RECIPES.md lists the situations
the helper already covers and the ones it does not.
-->

**Who is blocked**
Their role and licence (for example: staff with a Jira Software licence, a portal-only customer, an external contractor), and whether they count as internal in your organisation.

**On which page**
`/browse/<KEY>`, the Service Management agent view, or another page (please give the URL pattern).

**Why they are blocked**
- [ ] the issue's security level
- [ ] no Browse permission on the project
- [ ] the project is archived
- [ ] other:

**Who could let them in**
A field on the issue, the reporter, the assignee, the project lead, an administrator, a service desk, someone else?

**What the card may say**
What would help the viewer act, and to whom it may be shown.

**What the card must not say**
Anything that would be a disclosure in your organisation: that the issue exists, the level's name, the project's name, the people on it.

**Closest profile**
`full`, `jsm-only`, `no-jsm`, `secured-minimal`, `group-policy`, or none.

**How often it happens**
A rough idea is enough.
