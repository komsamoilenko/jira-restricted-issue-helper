// ============================================================================
//  Restricted-issue helper for Jira's "You can't view this issue" page
//  Version 1.1.0 -- ScriptRunner for Jira Data Center fragment
//  Fragment type: "Show a web panel"   Location: atl.header.after.scripts
//  Weight: 100   Condition: none        Licence: MIT (see LICENSE)
// ----------------------------------------------------------------------------
//  ASSEMBLED FILE. Built by build/assemble.py from profile "no-jsm" with
//  these modules: internal-mail-domain, people-reporter-assignee, secured.
//  Do not edit it by hand: edit src/ and rebuild (docs/EXTENDING.md). Only the
//  CONFIG block below is meant to be edited in a deployed copy.
//
//  Renders NOTHING except on the two dead-end pages, where the logged-in user
//  cannot see the issue. It then takes over the error block and draws ONE
//  card. Every decision is made here, on the server; the browser receives a
//  small JSON payload and the script that draws it.
//
//  Covered pages:
//   /browse/<KEY>                     Jira core, block .issue-error
//   /projects/<P>/queues[/...]/<KEY>  Jira Service Management agent view,
//                                     stock "Snap! You can't view this page"
//                                     (<section id="unlicensed-project-type">)
//
//  Modes, tried in MODE_ORDER; the generic card is the fallback:
//   portal  - a Service Management request the viewer CAN open on the portal
//   share   - a request the viewer cannot open, but whose reporter can add
//             them with the portal Share button
//   moved   - it USED to be a request but was moved out of the service desk
//   secured - the issue's security level is what hides it: say so, point at
//             the level's "add one person to this issue" field, and name who
//             can fill it in (only when every gate in docs/DESIGN.md holds)
//   generic - anything else -> Help Center + raise a request; identical for a
//             hidden issue and for a key that does not exist
//
//  Properties that hold for every mode: the payload defaults to the generic
//  card BEFORE any lookup, so an exception degrades to a usable card; the JSON
//  payload is HTML-escaped before it enters the inline <script>, and the
//  client inserts every server value as a text node; the client script
//  carries NO comments (build/strip_client_comments.py --check enforces it).
// ============================================================================
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

// >>> CONFIG =================================================================
//  Every deployment-specific value lives in this block. Nothing else in the
//  assembled file needs editing; docs/CONFIG.md explains each one.
//  The defaults are PLACEHOLDERS: until you replace them, the restricted card
//  never fires and "Raise a request" points at a request type that does not
//  exist on your instance.

// Where "Raise a request" and "Ask for access" lead: the create page of the
// Service Management request type that handles access questions.
// Format: /servicedesk/customer/portal/<portal id>/create/<request type id>
final String FALLBACK_URL = '/secure/ContactAdministrators!default.jspa'

// Per-project escalation targets, by project key. A project not listed here
// uses FALLBACK_URL. Read only by cards that already confirm the issue
// exists; the generic card always uses FALLBACK_URL, so a hidden issue and a
// missing key still get the same card.
final Map    ESCALATION_BY_PROJECT = [:]

// The customer portal's Help Center (stock Jira Service Management path).
final String HELP_CENTER  = '/secure/Dashboard.jspa'

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
final List   MODE_ORDER   = ['secured']

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
    openHelpCenter       : 'Go to the dashboard',
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
    securedLeadNoField   : 'There is no field on this issue for letting one more person in. If you need it, ask whoever shared the link with you, or contact the Jira administrators.',
    securedMessage       : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to its "{field}" field? That opens this one issue to me and nothing else. Thank you.',
    securedMessageFieldUnnamed : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to the field on the issue that lets one more person see it? That opens this one issue to me and nothing else. Thank you.',
    securedOpenAgain     : 'Added already? Open {key}',
    securedEscalateField : 'Nobody to ask? Contact the Jira administrators',
    securedEscalateNoField : 'Something else? Contact the Jira administrators',
    // generic
    genericTitle         : "You can't view this issue",
    genericTitleAgent    : 'This page is for service desk agents',
    genericText          : 'It may have been deleted, or you may not have permission to view it. If someone sent you this link, ask them for access.',
    genericTextAgent     : 'Queues are the Jira Service Management agent view, and only agents can open them. You almost certainly do not need that: requests you raised are all listed in the Help Center, and if you need access to a particular one, or help with anything else, raise a request and we will sort it out.',
    genericEscalate      : 'Still stuck? Contact the Jira administrators',
]
// <<< CONFIG =================================================================

// >>> DECIDE -- build/sync_tests.py copies everything from here down to
//               "<<< DECIDE" verbatim into tests/decision_test*.groovy, so
//               the decision tests run the deployed logic, not a copy of it.

// core/context.groovy -- shared helpers and the mode registry.
// Always built in, first in the DECIDE section. Everything here is read-only.

// The registry. Each mode module adds one entry:
//   MODES['name'] = { ctx -> a payload map, or null for "not my case" }
// core/decide.groovy tries them in MODE_ORDER and takes the first answer.
def MODES = [:]

// A mail address for the copy-ready messages. An account without one falls
// back to its username rather than rendering "add me (null)"; where usernames
// are e-mail addresses, that is still a usable address.
def mailOf = { u ->
    def m = u.getEmailAddress() ?: ''
    return m.contains('@') ? m : u.getName()
}

// Application access: true for an account that holds any licensed application
// role (Jira Software, Jira Core, a Service Management agent seat), false for
// portal-only customers and for anonymous. This is the "is not a portal-only
// customer" gate of the share, moved and secured cards. 1.0.0 asked
// GlobalPermissionKey.USE for the same thing; that key has been deprecated
// since Jira 7.0, and ApplicationRoleManager.hasAnyRole is its documented
// successor (present unchanged from Jira 8.0 to 11.x).
def hasAppAccess = { u ->
    u != null && ComponentAccessor.getComponent(ApplicationRoleManager).hasAnyRole(u)
}

// Does this viewer pass the issue's security level? True when the issue has
// none. getUsersSecurityLevels is documented as "can be null", hence ?: [].
def passesSecurity = { issue, u ->
    Long levelId = issue?.getSecurityLevelId()
    if (levelId == null) { return true }
    def levels = ComponentAccessor.getComponent(IssueSecurityLevelManager).getUsersSecurityLevels(issue, u) ?: []
    return levels.any { it.getId() == levelId }
}

// Service Management project? Gate on the project TYPE, not merely on the
// request-type field: issues moved out of a service desk keep the field, and
// the portal lookup throws for them.
def isServiceDeskProject = { proj ->
    proj != null && proj.getProjectTypeKey()?.getKey() == 'service_desk'
}

// Does the issue carry a Customer Request Type value (RT_FIELD)?
def hasRequestType = { issue ->
    def rtField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(RT_FIELD)
    return issue != null && rtField != null && issue.getCustomFieldValue(rtField) != null
}

// Where "Raise a request" leads for this project. Only for cards that already
// confirm the issue exists; the generic card must keep using FALLBACK_URL.
def escalationFor = { proj ->
    (proj != null ? ESCALATION_BY_PROJECT[proj.getKey()] : null) ?: FALLBACK_URL
}

// policy/internal-mail-domain.groovy -- who counts as internal: e-mail domain,
// and by default the username as well (CONFIG: INTERNAL_MAIL_DOMAINS,
// INTERNAL_REQUIRE_USERNAME). See docs/DESIGN.md, "Who counts as internal".
// Alternative: policy/internal-group.groovy. Build exactly one of them.

// An address qualifies when it holds exactly one "@" and its whole domain
// matches one of the patterns.
def internalAddr = { String a ->
    def m = (a ?: '').toLowerCase()
    int at = m.lastIndexOf('@')
    return at > 0 && m.indexOf('@') == at &&
           INTERNAL_MAIL_DOMAINS.any { p -> m.substring(at + 1) ==~ p }
}

def isInternal = { u ->
    u != null &&
    (!INTERNAL_REQUIRE_USERNAME || internalAddr(u.getName())) && internalAddr(u.getEmailAddress())
}

// policy/people-reporter-assignee.groovy -- who the restricted card may name
// as able to add the viewer: the reporter first, then the assignee, each only
// while that person is active, is not the viewer, has a mail address, is not
// an automation account (BOT_NAMES), and can see AND edit this issue.
// To name someone else (a project lead, a component lead), write another
// module that provides peopleFor with the same shape:
//   [[name: 'Display Name', role: 'reporter'], ...]   at most two entries.

def peopleFor = { ctx ->
    def issue = ctx.issue
    def user  = ctx.user
    def pm    = ctx.pm
    def im    = ComponentAccessor.getIssueManager()
    def byName = new LinkedHashMap()
    [[issue.getReporter(), TEXT.roleReporter], [issue.getAssignee(), TEXT.roleAssignee]].each { pr ->
        def u = pr[0]
        if (u == null || u.getName() == user.getName() || !u.isActive()) { return }
        if (!(u.getEmailAddress() ?: '').contains('@')) { return }
        if (BOT_NAMES.contains((u.getDisplayName() ?: '').toLowerCase())) { return }
        if (!byName.containsKey(u.getName())) {
            if (!pm.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, u) ||
                !pm.hasPermission(ProjectPermissions.EDIT_ISSUES, issue, u) ||
                !im.isEditable(issue, u)) { return }
            byName.put(u.getName(), [name: u.getDisplayName(), roles: []])
        }
        byName.get(u.getName()).roles.add(pr[1])
    }
    return byName.values().collect { [name: it.name, role: it.roles.join(' ' + TEXT.roleAnd + ' ')] }
}

// modes/secured.groovy -- the issue is hidden by its SECURITY LEVEL, and the
// level is the only thing stopping this viewer. Says so, points at the
// level's own "add one person to this one issue" field, and names who can
// fill it in. Every gate below must hold; the first failure returns null and
// the caller keeps its generic card (docs/DESIGN.md, "The restricted card's
// gates, in order"). DISCLOSURE and DISCLOSURE_BY_LEVEL trim what the card
// says once the gates have passed; they never widen the audience.

MODES['secured'] = { ctx ->
    if (!SECURED_CARD) { return null }
    def issue = ctx.issue
    def user  = ctx.user
    if (issue == null) { return null }
    Long levelId = issue.getSecurityLevelId()
    if (levelId == null) { return null }

    // Cheapest gates first. Portal-only customers must never be told an issue
    // exists, and neither must anyone the internal-viewer policy rejects.
    if (!isInternal(user)) { return null }
    if (!hasAppAccess(user)) { return null }
    def islm  = ComponentAccessor.getComponent(IssueSecurityLevelManager)
    def level = islm.getSecurityLevel(levelId)
    // Ids are compared as Long whatever the CONFIG list holds (12345 or
    // 12345L), so a missing L suffix can neither switch the card off nor
    // skip an exclusion.
    def schemesInScope = SECURED_SCHEMES.collect { it as Long }
    def levelsToSkip   = SECURED_SKIP_LEVELS.collect { it as Long }
    if (level == null || !schemesInScope.contains(level.getSchemeId() as Long) ||
        levelsToSkip.contains(levelId as Long)) { return null }
    // Already on the level: then the level is not what blocks them.
    if (passesSecurity(issue, user)) { return null }

    def proj = ctx.proj
    def im   = ComponentAccessor.getIssueManager()
    // Archived, or a workflow step with jira.issue.editable=false: nobody can
    // fill any field in, so there is no honest advice to give.
    if (proj == null || proj.isArchived() || !im.isEditable(issue)) { return null }

    // The level's own escape hatch: a MULTI-user picker the level grants on
    // (a "can also see" style field). Single pickers in the same grants are
    // typically role fields (a reviewer, a manager), and asking to be put
    // there would displace a person, so they never qualify.
    def cfm = ComponentAccessor.getCustomFieldManager()
    def candidates = ComponentAccessor.getComponent(IssueSecuritySchemeManager)
        .getPermissionsBySecurityLevel(levelId)
        .findAll { it.getType() == 'userCF' }
        .collect { cfm.getCustomFieldObject(it.getParameter() as String) }
        .findAll { cf -> cf != null &&
                   (cf.getCustomFieldType()?.getKey() ?: '').endsWith(':multiuserpicker') &&
                   cf.getRelevantConfig(issue) != null }

    // ...and it must really be on this issue's EDIT screen. The renderer's
    // per-field lookup answers with an item even for fields that are NOT on
    // the screen, so walk the tabs instead. Built only when there is a
    // candidate at all. Several qualifying fields: SECURED_FIELD_PREFERENCE
    // decides, else the lowest field id.
    def field = null
    if (!candidates.isEmpty()) {
        def onEdit = [] as Set
        try {
            def r = ComponentAccessor.getFieldScreenRendererFactory()
                        .getFieldScreenRenderer(issue, IssueOperations.EDIT_ISSUE_OPERATION)
            r.getFieldScreenRenderTabs().each { t ->
                t.getFieldScreenRenderLayoutItems().each { li ->
                    if (li.isShow(issue)) { onEdit.add(li.getOrderableField()?.getId()) }
                }
            }
        } catch (Throwable ignoredScreen) {
            onEdit.clear()      // unknown screen -> offer no field rather than a wrong one
        }
        def usable = candidates.findAll { onEdit.contains(it.getId()) }
        def preferred = SECURED_FIELD_PREFERENCE.collect { p ->
            String s = p.toString()
            s.startsWith('customfield_') ? s : 'customfield_' + s
        }
        field = preferred.findResult { p -> usable.find { it.getId() == p } } ?:
                usable.sort { it.getIdAsLong() }.find { true }
    }

    // Would the viewer actually get in? Issue security is a second lock on top
    // of the permission scheme. Project-level Browse is NOT the test: it is
    // true for everyone on projects that grant Browse through the reporter or
    // a user field. hasSchemePermission consults the permission scheme only
    // (issue security is a separate check that PermissionManager adds on
    // top), so it answers "would the scheme let this viewer browse THIS
    // issue"; its last argument, issueCreation, is false because the issue
    // exists. Failing that, accept a scheme that grants Browse through the
    // very field we suggest. hasSchemePermission is marked @Internal by
    // Atlassian (unchanged from Jira 8.0 to 11.x); if it ever disappears, the
    // call throws, the mode returns nothing, and the viewer keeps the generic
    // card -- the safe direction.
    def psm = ComponentAccessor.getPermissionSchemeManager()
    boolean schemeLetsIn = psm.hasSchemePermission(ctx.BROWSE, issue, user, false)
    if (!schemeLetsIn && field != null) {
        def scheme = psm.getSchemeFor(proj)
        schemeLetsIn = scheme != null && psm.getPermissionSchemeEntries(scheme, ctx.BROWSE)
                          .any { it.getType() == 'userCF' && it.getParameter() == field.getId() }
    }
    if (!schemeLetsIn) { return null }

    // What the card may say (DISCLOSURE, with per-level overrides). People are
    // named only when there is a field to fill in.
    def perLevel = DISCLOSURE_BY_LEVEL.find { k, v -> (k as Long) == (levelId as Long) }?.value ?: [:]
    def disc = DISCLOSURE + perLevel
    def people = (field != null && disc.people) ? peopleFor(ctx) : []

    return [mode: 'secured', issueKey: issue.getKey(), issueUrl: '/browse/' + issue.getKey(),
            levelName: disc.levelName ? (level.getName() ?: '').trim() : '',
            fieldName: (field != null && disc.fieldName) ? field.getName() : null,
            hasField: field != null,
            people: people,
            myMail: mailOf(user), fallbackUrl: escalationFor(proj)]
}

// core/decide.groovy -- decides which card (if any) this user gets for this
// issue on this page. Always built in, last in the DECIDE section.

// null = the page renders fine for them -> stay out of the way.
def decide = { user, issue, String key, String pageKind ->
    def pm     = ComponentAccessor.getPermissionManager()
    def BROWSE = ProjectPermissions.BROWSE_PROJECTS

    //  /browse/   is gated on Browse Projects.
    //  agent view is gated on the Service Management AGENT LICENCE first: a
    //  viewer without one is bounced to the "Snap!" page even where Browse
    //  would have let them in, so Browse alone is the wrong test there.
    boolean pageWorks
    if (pageKind == 'browse') {
        pageWorks = issue != null && pm.hasPermission(BROWSE, issue, user)
    } else {
        boolean isAgent = false
        try {
            isAgent = ComponentAccessor.getComponent(ApplicationAuthorizationService)
                          .canUseApplication(user, ApplicationKey.valueOf('jira-servicedesk'))
        } catch (Throwable ignoredLicence) {
            isAgent = false     // unknown -> compute the card; the DOM gate
        }                       // still keeps it off a page that renders
        pageWorks = isAgent && (issue == null || pm.hasPermission(BROWSE, issue, user))
    }
    if (pageWorks) { return null }

    // Safe default from here on: any failure below degrades to this card
    // rather than blanking the helper, and a viewer who fails every gate sees
    // the same card whether the issue exists or not. FALLBACK_URL on purpose,
    // never a per-project link: the generic card must not depend on the
    // project.
    def payload = [mode: 'generic', helpCenter: HELP_CENTER, fallbackUrl: FALLBACK_URL]

    def proj = null
    try {
        proj = issue?.getProjectObject()
    } catch (Throwable ignoredProj) {
        proj = null             // keep the generic card
    }
    def ctx = [user: user, issue: issue, key: key, pageKind: pageKind,
               proj: proj, pm: pm, BROWSE: BROWSE]

    // First mode in MODE_ORDER that answers wins. A mode that is not in this
    // build is skipped; a mode that throws is treated as "no answer".
    for (String name : MODE_ORDER) {
        def mode = MODES[name] as Closure
        if (mode == null) { continue }
        def answer = null
        try {
            answer = mode(ctx)
        } catch (Throwable ignoredMode) {
            answer = null
        }
        if (answer instanceof Map && answer.mode) {
            payload = answer
            break
        }
    }
    return payload
}

// <<< DECIDE

// core/entry.groovy -- the fragment's entry point: which page is this, who is
// looking, which issue. Always built in, between DECIDE and RENDER.

def payload  = null
String pageKind = null

try {
    // Case-insensitive, and the key is then taken from the resolved issue,
    // not from the URL: Jira serves lowercase keys, and a moved issue still
    // answers on its old key. `req` stays untyped on purpose: Jira 11 returns
    // a jakarta.servlet request here, earlier versions a javax.servlet one.
    def req = ExecutingHttpRequest.get()
    def uri = req?.getRequestURI() ?: ''
    def mBrowse = (uri =~ '(?i)^/browse/([a-z][a-z0-9_]*-[0-9]+)')
    def mAgent  = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?/([a-z][a-z0-9_]*-[0-9]+)$')
    def mQueues = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?$')
    pageKind = mBrowse ? 'browse' : ((mAgent || mQueues) ? 'agent' : null)
    if (pageKind && PAGES.contains(pageKind)) {
        def key  = mBrowse ? mBrowse[0][1].toUpperCase()
                           : (mAgent ? mAgent[0][1].toUpperCase() : null)
        def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
        if (user) {
            def issue = key ? ComponentAccessor.getIssueManager().getIssueObject(key) : null
            if (issue != null) { key = issue.getKey() }   // canonical key, not the URL's
            payload = decide(user, issue, key, pageKind)
        }
    } else {
        pageKind = null
    }
} catch (Throwable ignored) {
    payload = null          // never break a page over a helper
}

// >>> RENDER -- build/sync_tests.py copies this block verbatim into
//               tests/render_test.groovy. The client script inside must
//               stay free of comments.
// render/render.groovy -- payload -> inline client script. Always built in,
// the RENDER section. build/assemble.py replaces the @@CLIENT@@ line with
// render/client.js, full-line comments removed, and then runs
// build/strip_client_comments.py --check on the result.

if (payload) {
    payload.page = pageKind        // 'browse' | 'agent' -- picks the host node
                                   // and the wording of the card
    // JsonOutput does not escape '<'; without this a value containing
    // "</script>" would terminate the inline block. These are legal JSON
    // escapes, so the JS literal restores them unchanged.
    def safeJson = { obj ->
        JsonOutput.toJson(obj).replace('<', '\\u003c')
                              .replace('>', '\\u003e')
                              .replace('&', '\\u0026')
    }
    def dataJson = safeJson(payload)
    def textJson = safeJson(TEXT)
    // Client script. Inside the dollar-slashy string below, a dollar sign
    // followed by a name interpolates and dollar-slash is an escaped slash,
    // so the block holds exactly two interpolations (dataJson, textJson) and
    // no other dollar sign. It carries no comments: what it does is explained
    // at the top of render/client.js and in docs/DESIGN.md.
    writer.write($/
<script>
(function () {
  if (window.__jsmBrowseErrorHelper) { return; }
  window.__jsmBrowseErrorHelper = true;

  var d = $dataJson;
  var T = $textJson;
  var COPY_LABEL = T.copyLabel;
  var AGENT = (d.page === 'agent');

  var CSS = [
    '.jbh-wrap{max-width:520px;margin:8px auto 0;padding:0 16px;box-sizing:border-box;text-align:center;',
    'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;}',
    '.jbh-card{position:relative;padding:28px 28px 24px;border-radius:8px;background:#FFFFFF;',
    'border:1px solid #DFE1E6;box-shadow:0 1px 1px rgba(9,30,66,.10),0 0 1px rgba(9,30,66,.13);',
    'opacity:0;transform:translateY(10px);animation:jbh-in .36s cubic-bezier(.2,0,0,1) .05s forwards;}',
    '@keyframes jbh-in{to{opacity:1;transform:none}}',
    '.jbh-badge{width:52px;height:52px;margin:0 auto 16px;border-radius:50%;display:flex;',
    'align-items:center;justify-content:center;color:#0052CC;background:#E9F2FF;',
    'box-shadow:0 0 0 7px rgba(233,242,255,.5);transform:scale(.88);',
    'animation:jbh-pop .42s cubic-bezier(.2,0,0,1) .14s forwards;}',
    '@keyframes jbh-pop{to{transform:scale(1)}}',
    '.jbh-badge svg{display:block}',
    '.jbh-title{margin:0 0 8px;font-size:16px;line-height:1.32;font-weight:600;color:#172B4D;letter-spacing:-.003em}',
    '.jbh-text{margin:0 0 20px;font-size:13.5px;line-height:1.55;color:#5E6C84}',
    '.jbh-who{display:inline-flex;align-items:center;gap:8px;margin:0 0 16px;padding:6px 14px 6px 6px;',
    'border-radius:20px;background:#F4F5F7;font-size:13px;color:#172B4D;max-width:100%;overflow-wrap:anywhere}',
    '.jbh-av{flex:0 0 24px;width:24px;height:24px;border-radius:50%;background:#0052CC;color:#FFFFFF;display:flex;',
    'align-items:center;justify-content:center;font-size:11px;font-weight:700;letter-spacing:.2px}',
    '.jbh-quote{position:relative;margin:0 0 16px;padding:14px 16px;border-radius:6px;',
    'background:#F7F8F9;border:1px solid #DFE1E6;text-align:left;font-size:12.5px;line-height:1.6;',
    'color:#42526E;word-break:break-word}',
    '.jbh-quote b{display:block;margin-bottom:4px;color:#172B4D;font-weight:600}',
    '.jbh-people{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;margin:0 0 16px}',
    '.jbh-people .jbh-who{margin:0}',
    '.jbh-role{color:#5E6C84;font-size:12px}',
    '.jbh-actions{display:flex;flex-direction:column;align-items:center;gap:12px}',
    '.jbh-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;height:36px;',
    'padding:0 18px;border-radius:4px;font-size:14px;font-weight:500;text-decoration:none;',
    'border:none;cursor:pointer;font-family:inherit;',
    'transition:background .15s ease,box-shadow .15s ease,transform .15s ease}',
    '.jbh-btn-primary{background:#0052CC;color:#FFFFFF !important;box-shadow:0 1px 2px rgba(9,30,66,.2)}',
    '.jbh-btn-primary:hover{background:#0065FF;transform:translateY(-1px);box-shadow:0 4px 8px rgba(9,30,66,.16)}',
    '.jbh-btn-primary:active{background:#0747A6;transform:none;box-shadow:none}',
    '.jbh-btn-done{background:#216E4E !important;box-shadow:none !important;transform:none !important}',
    '.jbh-btn-warn{background:#974F0C !important}',
    '.jbh-btn svg{opacity:.9}',
    '.jbh-link{font-size:13px;color:#42526E;text-decoration:none;border-bottom:1px solid transparent;',
    'transition:color .15s ease,border-color .15s ease}',
    '.jbh-link:hover{color:#0052CC;border-bottom-color:#0052CC}',
    '.jbh-btn:focus-visible,.jbh-link:focus-visible{box-shadow:0 0 0 2px #FFFFFF,0 0 0 4px #0052CC}',
    '@media (prefers-reduced-motion:reduce){.jbh-card,.jbh-badge{animation:none;opacity:1;transform:none}}'
  ].join('');

  var ICON_PORTAL = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><path d='M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6'></path><polyline points='15 3 21 3 21 9'></polyline><line x1='10' y1='14' x2='21' y2='3'></line></svg>";
  var ICON_HELP   = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><circle cx='12' cy='12' r='10'></circle><path d='M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3'></path><line x1='12' y1='17' x2='12.02' y2='17'></line></svg>";
  var ICON_SHARE  = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'></path><circle cx='9' cy='7' r='4'></circle><line x1='19' y1='8' x2='19' y2='14'></line><line x1='22' y1='11' x2='16' y2='11'></line></svg>";
  var ICON_ARROW  = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><line x1='5' y1='12' x2='19' y2='12'></line><polyline points='12 5 19 12 12 19'></polyline></svg>";
  var ICON_LOCK   = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><rect x='3' y='11' width='18' height='11' rx='2' ry='2'></rect><path d='M7 11V7a5 5 0 0 1 10 0v4'></path></svg>";
  var ICON_COPY   = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><rect x='9' y='9' width='13' height='13' rx='2'></rect><path d='M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'></path></svg>";

  function fmt(template, vars) {
    return String(template).replace(/\{([A-Za-z]+)\}/g, function (all, name) {
      return (vars && Object.prototype.hasOwnProperty.call(vars, name)) ? String(vars[name]) : all;
    });
  }

  function style() {
    if (document.getElementById('jbh-style')) { return; }
    var s = document.createElement('style');
    s.id = 'jbh-style';
    s.appendChild(document.createTextNode(CSS));
    (document.head || document.documentElement).appendChild(s);
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) { n.className = cls; }
    if (text) { n.appendChild(document.createTextNode(text)); }
    return n;
  }

  function withIcon(node, svg) {
    var s = document.createElement('span');
    s.innerHTML = svg;
    node.appendChild(s);
    return node;
  }

  function tokens(name) {
    var out = [];
    var parts = (name || '').split(' ');
    for (var i = 0; i < parts.length; i++) {
      var t = parts[i].replace(/[^A-Za-z\u00C0-\u024F'-]/g, '');
      if (t) { out.push(t); }
    }
    return out;
  }

  function greetingFor(name) {
    var t = tokens(name);
    var first = t.length ? t[0] : '';
    var rawFirst = (name || '').split(' ')[0];
    var usable = first.length > 2 && first === rawFirst && first !== first.toUpperCase();
    return usable ? fmt(T.greetingNamed, { first: first }) : T.greetingPlain;
  }

  function greeting() { return greetingFor(d.reporterName); }

  function initialsOf(name) {
    var t = tokens(name);
    if (!t.length) { return '?'; }
    var a = t[0].charAt(0);
    var b = t.length > 1 ? t[t.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  function initials() { return initialsOf(d.reporterName); }

  function origin() {
    return window.location.protocol + '//' + window.location.host;
  }

  function shareMessage() {
    return greeting() + ' ' + fmt(T.shareMessage,
      { key: d.issueKey, url: origin() + d.portalUrl, mail: d.myMail });
  }

  function levelPhrase() {
    return d.levelName ? fmt(T.levelNamed, { level: d.levelName }) : T.levelUnnamed;
  }

  function hasField() {
    return !!(d.fieldName || d.hasField);
  }

  function securedLead() {
    var n = (d.people || []).length;
    var s = fmt(T.securedLead, { key: d.issueKey, levelPhrase: levelPhrase() });
    if (hasField()) {
      s += ' ' + (d.fieldName ? fmt(T.securedLeadField, { field: d.fieldName }) : T.securedLeadFieldUnnamed);
      if (n > 1) {
        s += ' ' + T.securedLeadMany;
      } else if (n === 1) {
        s += ' ' + T.securedLeadOne;
      } else {
        s += ' ' + T.securedLeadNobody;
      }
      if (AGENT) {
        s += ' ' + T.securedLeadAgent;
      }
    } else {
      s += ' ' + T.securedLeadNoField;
    }
    return s;
  }

  function securedMessage() {
    var ppl = d.people || [];
    var body = d.fieldName
      ? fmt(T.securedMessage, { url: origin() + d.issueUrl, mail: d.myMail, field: d.fieldName })
      : fmt(T.securedMessageFieldUnnamed, { url: origin() + d.issueUrl, mail: d.myMail });
    return (ppl.length === 1 ? greetingFor(ppl[0].name) : T.greetingPlain) + ' ' + body;
  }

  function copyText(text, btn) {
    if (btn.getAttribute('data-jbh-busy') === '1') { return; }

    function settle(label, cls) {
      btn.setAttribute('data-jbh-busy', '1');
      btn.textContent = '';
      btn.appendChild(document.createTextNode(label));
      btn.className = 'jbh-btn jbh-btn-primary ' + cls;
      setTimeout(function () {
        btn.textContent = '';
        btn.appendChild(document.createTextNode(COPY_LABEL));
        withIcon(btn, ICON_COPY);
        btn.className = 'jbh-btn jbh-btn-primary';
        btn.removeAttribute('data-jbh-busy');
      }, 2200);
    }
    function ok() { settle(T.copied, 'jbh-btn-done'); }
    function fail() { settle(T.copyFailed, 'jbh-btn-warn'); }

    function legacy() {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', 'readonly');
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      var done = false;
      try { done = document.execCommand('copy'); } catch (e) { done = false; }
      document.body.removeChild(ta);
      if (done) { ok(); } else { fail(); }
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(ok, legacy);
    } else {
      legacy();
    }
  }

  function card() {
    var wrap = el('div', 'jbh-wrap');
    wrap.id = 'jsm-browse-error-helper';
    var box = el('div', 'jbh-card');
    var badge = el('div', 'jbh-badge');
    var actions = el('div', 'jbh-actions');

    if (d.mode === 'portal') {
      badge.innerHTML = ICON_PORTAL;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.portalTitle));
      box.appendChild(el('p', 'jbh-text', AGENT ? T.portalTextAgent : T.portalText));

      var go = el('a', 'jbh-btn jbh-btn-primary');
      go.href = d.portalUrl;
      go.appendChild(document.createTextNode(T.portalOpen));
      withIcon(go, ICON_ARROW);
      actions.appendChild(go);

      var all = el('a', 'jbh-link', T.portalMyRequests);
      all.href = d.myRequests;
      actions.appendChild(all);

    } else if (d.mode === 'moved') {
      badge.innerHTML = ICON_PORTAL;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.movedTitle));

      var p1 = el('p', 'jbh-text');
      p1.appendChild(document.createTextNode(
        (d.oldKey ? fmt(T.movedFrom, { oldKey: d.oldKey, project: d.projectName })
                  : fmt(T.movedFromUnknown, { project: d.projectName })) + ' '));
      var keyLink = el('a', 'jbh-link');
      keyLink.href = d.issueUrl;
      keyLink.appendChild(document.createTextNode(d.issueKey));
      p1.appendChild(keyLink);
      p1.appendChild(document.createTextNode(T.movedAfterLink));
      box.appendChild(p1);

      box.appendChild(el('p', 'jbh-text',
        fmt(T.movedNoAccess, { project: d.projectName, key: d.issueKey })));

      var ask = el('a', 'jbh-btn jbh-btn-primary');
      ask.href = d.fallbackUrl;
      ask.appendChild(document.createTextNode(T.movedAsk));
      withIcon(ask, ICON_ARROW);
      actions.appendChild(ask);

      var open = el('a', 'jbh-link');
      open.href = d.issueUrl;
      open.appendChild(document.createTextNode(fmt(T.openKey, { key: d.issueKey })));
      actions.appendChild(open);

    } else if (d.mode === 'share') {
      badge.innerHTML = ICON_SHARE;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.shareTitle));
      box.appendChild(el('p', 'jbh-text',
        fmt(AGENT ? T.shareTextAgent : T.shareText, { key: d.issueKey })));

      var who = el('div', 'jbh-who');
      who.appendChild(el('div', 'jbh-av', initials()));
      who.appendChild(document.createTextNode(d.reporterName));
      box.appendChild(who);

      var quote = el('div', 'jbh-quote');
      quote.appendChild(el('b', null, T.messageHeadingThem));
      quote.appendChild(document.createTextNode(shareMessage()));
      box.appendChild(quote);

      var copy = el('button', 'jbh-btn jbh-btn-primary');
      copy.type = 'button';
      copy.setAttribute('aria-live', 'polite');
      copy.appendChild(document.createTextNode(COPY_LABEL));
      withIcon(copy, ICON_COPY);
      copy.addEventListener('click', function () { copyText(shareMessage(), copy); });
      actions.appendChild(copy);

      var esc = el('a', 'jbh-link', T.shareEscalate);
      esc.href = d.fallbackUrl;
      actions.appendChild(esc);

    } else if (d.mode === 'secured') {
      var ppl = d.people || [];
      badge.innerHTML = ICON_LOCK;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.securedTitle));
      box.appendChild(el('p', 'jbh-text', securedLead()));

      if (ppl.length) {
        var row = el('div', 'jbh-people');
        for (var k = 0; k < ppl.length; k++) {
          var chip = el('div', 'jbh-who');
          chip.appendChild(el('div', 'jbh-av', initialsOf(ppl[k].name)));
          chip.appendChild(document.createTextNode(ppl[k].name));
          chip.appendChild(el('span', 'jbh-role', ppl[k].role));
          row.appendChild(chip);
        }
        box.appendChild(row);
      }

      if (hasField()) {
        var sq = el('div', 'jbh-quote');
        sq.appendChild(el('b', null, ppl.length ? T.messageHeadingThem : T.messageHeading));
        sq.appendChild(document.createTextNode(securedMessage()));
        box.appendChild(sq);

        var sc = el('button', 'jbh-btn jbh-btn-primary');
        sc.type = 'button';
        sc.setAttribute('aria-live', 'polite');
        sc.appendChild(document.createTextNode(COPY_LABEL));
        withIcon(sc, ICON_COPY);
        sc.addEventListener('click', function () { copyText(securedMessage(), sc); });
        actions.appendChild(sc);

        var again = el('a', 'jbh-link', fmt(T.securedOpenAgain, { key: d.issueKey }));
        again.href = d.issueUrl;
        actions.appendChild(again);
      }

      var sEsc = el('a', 'jbh-link',
        hasField() ? T.securedEscalateField : T.securedEscalateNoField);
      sEsc.href = d.fallbackUrl;
      actions.appendChild(sEsc);

    } else {
      badge.innerHTML = ICON_HELP;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', AGENT ? T.genericTitleAgent : T.genericTitle));
      box.appendChild(el('p', 'jbh-text', AGENT ? T.genericTextAgent : T.genericText));

      if (AGENT) {
        var askUs = el('a', 'jbh-btn jbh-btn-primary');
        askUs.href = d.fallbackUrl;
        askUs.appendChild(document.createTextNode(T.raiseRequest));
        withIcon(askUs, ICON_ARROW);
        actions.appendChild(askUs);

        var hcLink = el('a', 'jbh-link', T.openHelpCenter);
        hcLink.href = d.helpCenter;
        actions.appendChild(hcLink);
      } else {
        var hc = el('a', 'jbh-btn jbh-btn-primary');
        hc.href = d.helpCenter;
        hc.appendChild(document.createTextNode(T.openHelpCenter));
        withIcon(hc, ICON_ARROW);
        actions.appendChild(hc);

        var raise = el('a', 'jbh-link', T.genericEscalate);
        raise.href = d.fallbackUrl;
        actions.appendChild(raise);
      }
    }

    box.appendChild(actions);
    wrap.appendChild(box);
    return wrap;
  }

  function inject() {
    var host = document.querySelector('.issue-error') ||
               document.getElementById('unlicensed-project-type');
    if (!host) { return false; }
    if (document.getElementById('jsm-browse-error-helper')) { return true; }
    style();
    var kids = [].slice.call(host.children);
    for (var i = 0; i < kids.length; i++) { kids[i].style.display = 'none'; }
    host.appendChild(card());
    return true;
  }

  var obs = null;
  function stopObserver() {
    if (obs) { obs.disconnect(); obs = null; }
  }

  function start() {
    if (inject()) { return; }
    var tries = 0;
    var timer = setInterval(function () {
      tries = tries + 1;
      if (inject() || tries > 40) { clearInterval(timer); stopObserver(); }
    }, 250);
    if (window.MutationObserver) {
      obs = new MutationObserver(function () {
        if (inject()) { clearInterval(timer); stopObserver(); }
      });
      obs.observe(document.body || document.documentElement, { childList: true, subtree: true });
      setTimeout(stopObserver, 15000);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
</script>
/$)
}
// <<< RENDER
