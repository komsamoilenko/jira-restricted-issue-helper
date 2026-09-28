// ============================================================================
//  Route test for dist/full.groovy        READ-ONLY, needs no data
// ----------------------------------------------------------------------------
//  The entry point decides which page a request is (browse, agent view, or
//  neither) and which key it names, and puts the context path back on the
//  card's links. Those two closures, route() and prefixLinks(), are pure
//  functions of the URI and the context path, so they are tested here with
//  strings only: no request, no user, no issue.
//
//  How to use: python build/sync_tests.py, paste the whole file into
//  ScriptRunner > Console, run. Every case must PASS.
// ============================================================================
// >>> COPY IMPORTS
import com.atlassian.jira.application.ApplicationRoleManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.security.IssueSecurityLevelManager
import com.atlassian.jira.permission.ProjectPermissions
import com.atlassian.jira.issue.operation.IssueOperations
import com.atlassian.jira.issue.security.IssueSecuritySchemeManager
import com.atlassian.application.api.ApplicationKey
import com.atlassian.jira.application.ApplicationAuthorizationService
import com.atlassian.jira.web.ExecutingHttpRequest
import groovy.json.JsonOutput
// <<< COPY IMPORTS
// >>> COPY CONFIG
// >>> CONFIG =================================================================
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
// every other scheme keep the generic card. Ids are compared as Long whether
// you write 12345 or 12345L (1.0.0 needed the L suffix; it is still the
// clearest way to write an id).
final List   SECURED_SCHEMES     = [12345L]

// Levels inside those schemes that must never get the card, because their
// NAME or their MEMBERS are what the level protects (compartments for people
// matters, for example). Level ids; compared as Long either way.
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

// Every piece of text the card shows, in one place. {placeholders} are filled
// in by the card: {key} issue key, {oldKey} its former key, {project} project
// name, {level} security level name, {levelPhrase} levelNamed or
// levelUnnamed, {field} field name, {mail} the viewer's e-mail address, {url}
// a full link, {first} a first name. Values reach the browser as JSON and are
// inserted as plain text, never as HTML. The card joins sentences and
// greetings with a single space.
final Map    TEXT = [
    // shared
    copyLabel            : 'Copy message',
    copied               : 'Copied to clipboard',
    copyFailed           : 'Copy it from the box above',
    greetingNamed        : 'Hi {first},',
    greetingPlain        : 'Hi,',
    messageHeadingThem   : 'Message to send them',
    messageHeading       : 'Message to send',
    raiseRequest         : 'Raise a request',
    openHelpCenter       : 'Open the Help Center',
    openKey              : 'Open {key}',
    // role labels on the people chips of the restricted card
    roleReporter         : 'reporter',
    roleAssignee         : 'assignee',
    roleAnd              : 'and',
    // portal
    portalTitle          : 'This request opens on the customer portal',
    portalText           : 'You do have access to it there. Requests like this one are handled on the portal rather than in this Jira view.',
    portalTextAgent      : 'The page you opened is the agent view, and it needs a Jira Service Management agent licence. You do not need one: this request is already open to you on the customer portal, where you can read it and add comments.',
    portalOpen           : 'Open this request',
    portalMyRequests     : 'See all of my open requests',
    // moved
    movedTitle           : 'This request moved out of the service desk',
    movedFrom            : '{oldKey} was moved into {project}, as',
    movedFromUnknown     : 'It was moved into {project}, as',
    movedAfterLink       : '. It is no longer on the customer portal, so the Help Center will not list it.',
    movedNoAccess        : 'You do not have access to {project} yet, which is why this page is empty. Send {key} to whoever asked you about it, or ask us for access.',
    movedAsk             : 'Ask for access',
    // share
    shareTitle           : 'Ask to be added to this request',
    shareText            : '{key} is a Service Management request you are not on. The person who raised it can add you as a Request participant in two clicks, with the Share button on the portal.',
    shareTextAgent       : '{key} is a Service Management request, and this page is the agent view, which needs an agent licence. You do not need one: being a Request participant is enough to read the request and comment on it. The person who raised it can add you in two clicks, with the Share button on the portal.',
    shareMessage         : 'I am trying to open {key} but I do not have access to it. Could you open {url} and use the Share button to add {mail} as a Request participant? That is enough for me to read the request and comment on it. Thank you.',
    shareEscalate        : 'Reporter unavailable? Raise a request',
    // secured (restricted by an issue security level)
    securedTitle         : 'This issue is restricted',
    levelNamed           : 'the security level "{level}"',
    levelUnnamed         : 'a security level',
    securedLead          : '{key} is protected by {levelPhrase}, so only the people and groups on that level can open it.',
    securedLeadField     : 'You do not have to join them: being added to its "{field}" field opens this one issue to you, and nothing else.',
    securedLeadFieldUnnamed : 'You do not have to join them: this issue has a field for letting one more person in, and being added to it opens this one issue to you, and nothing else.',
    securedLeadMany      : 'Either of the people below can do that in a few seconds.',
    securedLeadOne       : 'The person below can do that in a few seconds.',
    securedLeadNobody    : 'Anyone who can edit the issue can add you, so send the message below to whoever shared the link with you.',
    securedLeadAgent     : 'Once you are added, open it with the link below rather than from a queue: queues also need an agent licence.',
    securedLeadNoField   : 'There is no field on this issue for letting one more person in. If you need it, ask whoever shared the link with you, or raise a request.',
    securedMessage       : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to its "{field}" field? That opens this one issue to me and nothing else. Thank you.',
    securedMessageFieldUnnamed : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to the field on the issue that lets one more person see it? That opens this one issue to me and nothing else. Thank you.',
    securedOpenAgain     : 'Added already? Open {key}',
    securedEscalateField : 'Nobody to ask? Raise a request',
    securedEscalateNoField : 'Something else? Raise a request',
    // generic
    genericTitle         : "You can't view this issue",
    genericTitleAgent    : 'This page is for service desk agents',
    genericText          : 'It may have been deleted, or you may not have permission. If you were looking for a Service Management request, the Help Center lists every request you raised.',
    genericTextAgent     : 'Queues are the Jira Service Management agent view, and only agents can open them. You almost certainly do not need that: requests you raised are all listed in the Help Center, and if you need access to a particular one, or help with anything else, raise a request and we will sort it out.',
    genericEscalate      : 'Still stuck? Raise a request and we will help',
]
// <<< CONFIG =================================================================
// <<< COPY CONFIG
// >>> COPY ROUTE
// >>> ROUTE -- build/sync_tests.py copies this block into tests/route_test.groovy
// Which page is this, and which key does it name. Pure functions of the
// request URI and the context path, so they can be tested without a request.
//   route(uri, cp) -> [pageKind: 'browse' | 'agent' | null, key: 'ABC-1' | null]
// Case-insensitive; the key is then re-read from the resolved issue, because
// Jira serves lowercase keys and a moved issue still answers on its old key.
// A Jira served under a context path (for example /jira) reports URIs that
// start with it: it is stripped before matching, and prefixLinks() puts it
// back on every root-relative link the card carries.
def route = { String rawUri, String cp ->
    String uri = rawUri ?: ''
    String ctxPath = cp ?: ''
    if (ctxPath && (uri == ctxPath || uri.startsWith(ctxPath + '/'))) {
        uri = uri.substring(ctxPath.length())
    }
    // Each matcher is asked exactly once: a Matcher in boolean context calls
    // find(), and a second find() on the same matcher continues after the
    // first match instead of starting over, so it would say "no match".
    String kind = null
    String key  = null
    def mBrowse = (uri =~ '(?i)^/browse/([a-z][a-z0-9_]*-[0-9]+)')
    if (mBrowse.find()) {
        kind = 'browse'
        key  = mBrowse.group(1).toUpperCase()
    } else {
        def mAgent = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?/([a-z][a-z0-9_]*-[0-9]+)$')
        if (mAgent.find()) {
            kind = 'agent'
            key  = mAgent.group(1).toUpperCase()
        } else if (uri ==~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?$') {
            kind = 'agent'
        }
    }
    if (kind && !PAGES.contains(kind)) { kind = null; key = null }
    return [pageKind: kind, key: key]
}

// Puts the context path in front of every root-relative link of a payload.
// Protocol-relative links (//host/...) and links that already carry the
// prefix are left alone.
def prefixLinks = { Map p, String cp ->
    if (p == null || !cp) { return p }
    ['issueUrl', 'portalUrl', 'helpCenter', 'fallbackUrl', 'myRequests'].each { k ->
        def v = p[k]
        if (v instanceof String && v.startsWith('/') && !v.startsWith('//') && !v.startsWith(cp + '/')) {
            p[k] = cp + v
        }
    }
    return p
}
// <<< ROUTE
// <<< COPY ROUTE

def CASES = [
  // uri, context path, expected pageKind, expected key
  ['/browse/ABC-1',                          '',      'browse', 'ABC-1'],
  ['/browse/abc-12',                         '',      'browse', 'ABC-12'],
  ['/browse/ABC-1?page=history',             '',      'browse', 'ABC-1'],
  ['/browse/ABC-1/',                         '',      'browse', 'ABC-1'],
  ['/browse/A_B2-7',                         '',      'browse', 'A_B2-7'],
  ['/browse/',                               '',      null,     null],
  ['/browse/ABC',                            '',      null,     null],
  ['/browse/1ABC-1',                         '',      null,     null],
  ['/projects/HELP/queues/custom/12/HELP-5', '',      'agent',  'HELP-5'],
  ['/projects/HELP/queues/custom/12',        '',      'agent',  null],
  ['/projects/HELP/queues',                  '',      'agent',  null],
  ['/projects/HELP/queues/HELP-5',           '',      'agent',  'HELP-5'],
  ['/projects/HELP/issues/HELP-5',           '',      null,     null],
  ['/secure/Dashboard.jspa',                 '',      null,     null],
  ['/issues/?jql=key%3DABC-1',               '',      null,     null],
  // a Jira served under a context path
  ['/jira/browse/ABC-1',                     '/jira', 'browse', 'ABC-1'],
  ['/jira/projects/HELP/queues/custom/1/HELP-5', '/jira', 'agent', 'HELP-5'],
  ['/browse/ABC-1',                          '/jira', null,     null],
  ['/jiraX/browse/ABC-1',                    '/jira', null,     null],
  ['/jira',                                  '/jira', null,     null],
]

def LINKS = [
  // payload, context path, expected payload after prefixLinks
  [[mode: 'secured', issueUrl: '/browse/ABC-1', fallbackUrl: '/servicedesk/customer/portal/1/create/1'], '',
   [mode: 'secured', issueUrl: '/browse/ABC-1', fallbackUrl: '/servicedesk/customer/portal/1/create/1']],
  [[mode: 'secured', issueUrl: '/browse/ABC-1', fallbackUrl: '/servicedesk/customer/portal/1/create/1'], '/jira',
   [mode: 'secured', issueUrl: '/jira/browse/ABC-1', fallbackUrl: '/jira/servicedesk/customer/portal/1/create/1']],
  [[mode: 'portal', portalUrl: '/jira/servicedesk/customer/portal/1/HELP-5', myRequests: '//cdn.example.com/x', helpCenter: 'https://help.example.com/'], '/jira',
   [mode: 'portal', portalUrl: '/jira/servicedesk/customer/portal/1/HELP-5', myRequests: '//cdn.example.com/x', helpCenter: 'https://help.example.com/']],
  [[mode: 'generic', helpCenter: '/servicedesk/customer/portals', fallbackUrl: '/x', levelName: '/not-a-link'], '/jira',
   [mode: 'generic', helpCenter: '/jira/servicedesk/customer/portals', fallbackUrl: '/jira/x', levelName: '/not-a-link']],
  [null, '/jira', null],
]

def out = new StringBuilder()
int pass = 0, fail = 0
CASES.each { c ->
    def r = route(c[0], c[1])
    boolean ok = r.pageKind == c[2] && r.key == c[3]
    if (ok) { pass++ } else { fail++ }
    out.append((ok ? 'PASS ' : 'FAIL ') + 'route(' + c[0] + ', ' + c[1] + ') -> ' + r +
               (ok ? '' : '   expected [pageKind:' + c[2] + ', key:' + c[3] + ']') + '\n')
}
LINKS.each { c ->
    def p = c[0] == null ? null : new LinkedHashMap(c[0])
    def r = prefixLinks(p, c[1])
    boolean ok = r == c[2]
    if (ok) { pass++ } else { fail++ }
    out.append((ok ? 'PASS ' : 'FAIL ') + 'prefixLinks(' + c[0] + ', ' + c[1] + ') -> ' + r +
               (ok ? '' : '   expected ' + c[2]) + '\n')
}
String verdict = (fail == 0 && pass > 0) ? 'OK' : 'NOT OK'
return 'RESULT ' + verdict + '  SUMMARY pass=' + pass + ' fail=' + fail + '\n' + out.toString()
