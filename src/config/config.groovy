//  Every deployment-specific value lives in this block. Nothing else in the
//  assembled file needs editing; docs/CONFIG.md explains each one.
//  The defaults are PLACEHOLDERS: until you replace them, the restricted card
//  never fires and "Raise a request" points at a request type that does not
//  exist on your instance.

// Where "Raise a request" and "Ask for access" lead: the create page of the
// Service Management request type that handles access questions.
// Format: /servicedesk/customer/portal/<portal id>/create/<request type id>
final String FALLBACK_URL = '/servicedesk/customer/portal/1/create/1'

// Per-project escalation targets, by project key. A project not listed here
// uses FALLBACK_URL. Read only by cards that already confirm the issue
// exists; the generic card always uses FALLBACK_URL, so a hidden issue and a
// missing key still get the same card.
final Map    ESCALATION_BY_PROJECT = [:]

// The customer portal's Help Center (stock Jira Service Management path).
final String HELP_CENTER  = '/servicedesk/customer/portals'

// The viewer's own open requests on the customer portal (stock path).
final String MY_REQUESTS  = '/servicedesk/customer/user/requests?status=open'

// Id of the Service Management "Customer Request Type" custom field on your
// instance (Administration > Issues > Custom fields; the numeric id is in the
// field's configure link). An unknown id is harmless: the portal and share
// modes stop applying, and moved-issue detection falls back to key history.
final String RT_FIELD     = 'customfield_12345'

// Which dead-end pages the helper covers: 'browse' is /browse/<KEY>, 'agent'
// is the Service Management agent view. Remove one to leave that page alone.
final List   PAGES        = ['browse', 'agent']

// The order in which the modes are tried; the first one that answers wins,
// and the generic card is the fallback that always exists. A name whose
// module is not in this build is skipped, so the list can stay as it is
// across profiles. Remove a name to switch that mode off.
final List   MODE_ORDER   = ['portal', 'share', 'moved', 'secured']

// Kill switch for the "moved out of the service desk" card. false = every
// viewer it would cover gets the generic card instead.
final boolean MOVED_CARD  = true

// Kill switch for the restricted-issue ("secured") card. false = every viewer
// it covers gets the generic card again, exactly as before the mode existed.
final boolean SECURED_CARD = true

// Display names, compared in lower case, of automation and system accounts.
// They are never named as someone who can help, and never offered as the
// reporter to ask for a Share.
final List   BOT_NAMES    = ['jira automation', 'jira', 'automation for jira', 'anonymous']

// Internal-viewer policy, mail-domain variant: who may be told that a
// restricted issue exists. Each entry is a regular expression matched against
// the WHOLE domain part (after the "@", lower-cased) of an address:
// 'example\\.com' matches someone@example.com only, 'example\\.[a-z]+'
// matches any top-level domain. An empty list means nobody is internal, so
// the restricted card never fires.
final List   INTERNAL_MAIL_DOMAINS = ['example\\.com']

// true  = the USERNAME must match INTERNAL_MAIL_DOMAINS as well as the e-mail
//         address. Use this where usernames are e-mail addresses that only
//         administrators can change: a user who edits their own e-mail
//         address still cannot pass.
// false = the e-mail address alone decides. Choose this only where users
//         cannot change their own e-mail address.
final boolean INTERNAL_REQUIRE_USERNAME = true

// Internal-viewer policy, group variant (read only when the build includes
// policy/internal-group instead of policy/internal-mail-domain): the viewer
// must be in at least one of these groups. Use a group that is maintained to
// equal staff exactly; an empty list means nobody is internal.
final List   INTERNAL_GROUPS = ['jira-staff']

// Issue security SCHEMES whose levels may get the restricted card; levels of
// every other scheme keep the generic card. Keep the L suffix: getSchemeId()
// is a Long, and [12345].contains(12345L) is false, which would silently
// switch the card off everywhere.
final List   SECURED_SCHEMES     = [12345L]

// Levels inside those schemes that must never get the card, because their
// NAME or their MEMBERS are what the level protects (compartments for people
// matters, for example). Level ids, with the L suffix.
final List   SECURED_SKIP_LEVELS = [12346L, 12347L]

// What the restricted card may SAY once every gate has passed (the gates
// always run; this only trims the content):
//   levelName  name the security level              (false: "a security level")
//   fieldName  name the field that opens the issue  (false: "the field on the issue that lets one more person see it")
//   people     name the reporter and assignee       (false: "ask whoever shared the link with you")
final Map    DISCLOSURE = [levelName: true, fieldName: true, people: true]

// Per-level overrides of DISCLOSURE, by level id with the L suffix, for
// example [12348L: [people: false]] or [12349L: [levelName: false, people: false]].
final Map    DISCLOSURE_BY_LEVEL = [:]

// When a level grants access through several multi-user picker fields that
// are all on the edit screen, prefer these, in order (field ids such as
// 'customfield_10100', or just the number). Empty = the lowest field id wins.
final List   SECURED_FIELD_PREFERENCE = []
