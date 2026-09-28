// ============================================================================
//  Kill-switch test for dist/full.groovy     READ-ONLY
// ----------------------------------------------------------------------------
//  The same decide() as tests/decision_test.groovy, with SECURED_CARD = false
//  (build/sync_tests.py flips it in the CONFIG copy). Proves that switching
//  the restricted card off restores the generic card for every viewer it
//  covered, and leaves the other modes alone. Uses the synthetic accounts and
//  issues described in tests/decision_test.groovy; replace them the same way.
//  Paste the whole file into ScriptRunner > Console and run it.
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
// >>> COPY CONFIG_SECURED_OFF
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
final boolean SECURED_CARD = false

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

// Scope of the existence-only card (modes/restricted; off unless 'restricted'
// is in MODE_ORDER). It tells an internal viewer with application access that
// the issue exists and is closed to them, and nothing else:
//   'in-scope'  only levels of SECURED_SCHEMES minus SECURED_SKIP_LEVELS;
//   'all'       every issue hidden by a security level;
//   'any-issue' every issue the viewer cannot browse, level or not.
// Issues outside the scope keep the generic card. The 'missing' card ("no
// issue has this key") answers ONLY at 'any-issue' with 'restricted' also in
// MODE_ORDER: at a narrower scope it would sit next to generic cards for
// existing issues and confirm that every one of them exists. So at
// 'in-scope' and 'all' a missing key and an issue outside the scope look the
// same. Any other value keeps both modes silent.
final String RESTRICTED_SCOPE = 'in-scope'

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
    // restricted (existence only; modes/restricted, off unless in MODE_ORDER)
    restrictedTitle      : 'This issue is restricted',
    restrictedText       : '{key} exists, but you do not have permission to view it, so nothing about it can be shown here. Ask whoever shared the link with you, or raise a request.',
    restrictedTextAgent  : '{key} exists, but you do not have permission to view it, so nothing about it can be shown here. Ask whoever shared the link with you, or raise a request. Once you have access, open it with the link below rather than from a queue: queues also need an agent licence.',
    restrictedOpenAgain  : 'Got access? Open {key}',
    // missing (no issue with this key; modes/missing, off unless in MODE_ORDER)
    missingTitle         : 'No issue with this key',
    missingText          : 'There is no issue {key}. It may have been deleted, or the key may be mistyped. If you followed a link, ask whoever sent it.',
    // generic
    genericTitle         : "You can't view this issue",
    genericTitleAgent    : 'This page is for service desk agents',
    genericText          : 'It may have been deleted, or you may not have permission. If you were looking for a Service Management request, the Help Center lists every request you raised.',
    genericTextAgent     : 'Queues are the Jira Service Management agent view, and only agents can open them. You almost certainly do not need that: requests you raised are all listed in the Help Center, and if you need access to a particular one, or help with anything else, raise a request and we will sort it out.',
    genericEscalate      : 'Still stuck? Raise a request and we will help',
]
// <<< CONFIG =================================================================
// <<< COPY CONFIG_SECURED_OFF
// >>> COPY DECIDE
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

// Application access: true for an ACTIVE account that holds any licensed
// application role (Jira Software, Jira Core, a Service Management agent
// seat), false for portal-only customers, deactivated accounts and anonymous.
// This is the "is not a portal-only customer" gate of the share, moved and
// secured cards. 1.0.0 asked GlobalPermissionKey.USE for the same thing; that
// key is marked @Deprecated ("Use ApplicationAuthorizationService instead.
// Since v7.0") in every Javadoc from 8.0 to 11.x. 1.1.0 uses
// ApplicationRoleManager.hasAnyRole instead, present unchanged over the same
// range. hasAnyRole does not look at the account's status: a deactivated
// account that is still in a licensed group answers true, where USE answered
// false (measured on every account of one instance: about one deactivated
// account in six). With isActive() in front, the two agreed for every account.
def hasAppAccess = { u ->
    u != null && u.isActive() && ComponentAccessor.getComponent(ApplicationRoleManager).hasAnyRole(u)
}

// Test hook. Deployed, this stays null and costs nothing. The decision tests
// set it to a list before calling decide(), and decide() then records every
// exception a mode swallowed, so a mode that always throws cannot pass a
// negative test by accident.
def MODE_ERRORS = null

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

// policy/jsm.groovy -- Jira Service Management services for the portal and
// share modes. The classes are reached by name at run time, so the assembled
// file compiles and runs where Service Management is absent (every call is
// inside a try in the modes that use it, and a failure means "no card").

// Runs the body in the CUSTOMER context, which is how the portal itself
// decides who may open a request. The body is coerced to JSM's
// NoExceptionsCallable with Groovy's asType, which also gives the proxy
// sane equals/hashCode/toString (1.0.0 used a bare java.lang.reflect.Proxy).
def jsmInCustomerContext = { Closure body ->
    def ccs = ComponentAccessor.getOSGiComponentInstanceOfType(
        Class.forName('com.atlassian.servicedesk.api.customer.CustomerContextService'))
    def nec = Class.forName('com.atlassian.servicedesk.api.customer.NoExceptionsCallable')
    return ccs.runInCustomerContext(body.asType(nec))
}

// The portal URL of the request for a given person, or null when that person
// has no portal for the project (getPortalForProject throws rather than
// returning null).
def jsmPortalFor = { who, proj, String key ->
    try {
        def ps = ComponentAccessor.getOSGiComponentInstanceOfType(
            Class.forName('com.atlassian.servicedesk.api.portal.PortalService'))
        def portal = ps.getPortalForProject(who, proj)
        return portal ? ('/servicedesk/customer/portal/' + portal.getId() + '/' + key) : null
    } catch (Throwable ignoredInner) {
        return null
    }
}

// A current Service Management request: a service-desk project AND a value in
// the request-type field.
def isRequest = { ctx ->
    isServiceDeskProject(ctx.proj) && hasRequestType(ctx.issue)
}

// modes/portal.groovy -- a Service Management request the viewer CAN open on
// the customer portal: point them there instead of at the agent view.
// Reveals: a link to a request the viewer can already open.

MODES['portal'] = { ctx ->
    if (!isRequest(ctx)) { return null }
    // Permission checks run in the CUSTOMER context, which is how the portal
    // itself decides.
    def mine = jsmInCustomerContext { ->
        ctx.pm.hasPermission(ctx.BROWSE, ctx.issue, ctx.user) ? jsmPortalFor(ctx.user, ctx.proj, ctx.key) : null
    }
    if (!mine) { return null }
    return [mode: 'portal', portalUrl: mine,
            myRequests: MY_REQUESTS, fallbackUrl: escalationFor(ctx.proj)]
}

// modes/share.groovy -- a Service Management request the viewer is not on,
// whose reporter can add them as a Request participant with the portal's
// Share button. Runs after portal, so the viewer cannot open it themselves.
// Reveals: that the request exists, the reporter's display name, the portal
// link. Never the reporter's e-mail address.
// Gates: application access (never a portal-only customer), the issue's
// security level (a Share would not help someone the level blocks), a usable
// reporter (active, not the viewer, has a mail address, not a bot) who has
// access to the project's portal.

MODES['share'] = { ctx ->
    if (!isRequest(ctx)) { return null }
    def issue = ctx.issue
    def user  = ctx.user
    if (!passesSecurity(issue, user) || !hasAppAccess(user)) { return null }
    def rep = issue.getReporter()
    boolean repUsable = rep != null && rep.isActive() &&
        rep.getName() != user.getName() &&
        (rep.getEmailAddress() ?: '').contains('@') &&
        !BOT_NAMES.contains((rep.getDisplayName() ?: '').toLowerCase())
    if (!repUsable) { return null }
    def viaReporter = jsmInCustomerContext { -> jsmPortalFor(rep, ctx.proj, ctx.key) }
    if (!viaReporter) { return null }
    return [mode: 'share', issueKey: ctx.key, portalUrl: viaReporter,
            reporterName: rep.getDisplayName(),
            myMail: mailOf(user),
            fallbackUrl: escalationFor(ctx.proj)]
}

// modes/moved.groovy -- the issue USED to be a Service Management request and
// was moved out of the service desk. Its portal URL is dead for everyone and
// the Help Center will never list it again, so the generic card's "the Help
// Center lists every request you raised" would be actively wrong here.
// Reveals: that the issue exists, its current key and project name, its
// former key.
// Gates: application access (never a portal-only customer), the issue's
// security level. Kill switch: MOVED_CARD.

MODES['moved'] = { ctx ->
    if (!MOVED_CARD) { return null }
    def proj  = ctx.proj
    def issue = ctx.issue
    if (proj == null || isServiceDeskProject(proj)) { return null }

    // Key history is the reliable signal: it survives the destination project
    // dropping the request-type field, which a move does not always keep. The
    // field is kept as the fallback, because key history misses issues whose
    // SOURCE project key changed later.
    def prevSd = []
    try {
        def sdSet = [] as Set
        ComponentAccessor.getProjectManager().getProjectObjects().each { p ->
            if (isServiceDeskProject(p)) { sdSet.add(p.getKey()) }
        }
        prevSd = (ComponentAccessor.getIssueManager().getAllIssueKeys(issue.getId()) as Set)
                     .findAll { it != issue.getKey() && sdSet.contains(it.tokenize('-')[0]) }
                     .toList()
    } catch (Throwable ignoredKeys) { }
    boolean wasRequest = !prevSd.isEmpty() || hasRequestType(issue)
    if (!wasRequest) { return null }

    // Never confirm an issue's existence to a portal-only customer, never
    // bypass issue security. Everyone else keeps the generic card.
    if (!passesSecurity(issue, ctx.user) || !hasAppAccess(ctx.user)) { return null }
    return [mode: 'moved',
            issueKey: issue.getKey(),
            issueUrl: '/browse/' + issue.getKey(),
            projectName: proj.getName(),
            oldKey: prevSd.isEmpty() ? null : prevSd[0],
            fallbackUrl: escalationFor(proj)]
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

// modes/restricted.groovy -- existence only. The issue exists and this viewer
// may not see it: say that, and nothing else. No level name, no field, no
// people, no copy-ready message. For instances that do not want the card to
// say anything about a restricted issue, but want staff to tell "restricted"
// from "deleted" (its companion, modes/missing, covers the other half).
//
// OFF unless 'restricted' is in MODE_ORDER. Put it after 'secured' to use it
// as the fallback when the restricted card has nothing safe to say, or alone
// (profile exists-only). docs/DESIGN.md, "Existence only".
//
// Audience: the viewer passes the internal-viewer policy and holds
// application access. Scope, by RESTRICTED_SCOPE:
//   'in-scope'   issues whose level is in SECURED_SCHEMES and not in
//                SECURED_SKIP_LEVELS (default);
//   'all'        every issue hidden by a security level;
//   'any-issue'  every issue the viewer cannot browse, level or not (the
//                only scope at which modes/missing answers).
// An unknown value keeps this mode silent. Issues outside the scope keep the
// generic card, which is also what a missing key gets at these scopes, so
// they stay indistinguishable from missing keys.
// Reveals: that the issue exists and the viewer lacks permission. The card
// repeats the key the viewer used (ctx.urlKey), never the canonical key: an
// issue reached through an old key after a move would otherwise reveal its
// new project. Archived projects are not excluded: the statement is true for
// them too, and no advice is given that would need an editable issue.

MODES['restricted'] = { ctx ->
    def issue = ctx.issue
    def user  = ctx.user
    if (issue == null) { return null }
    if (!(RESTRICTED_SCOPE in ['in-scope', 'all', 'any-issue'])) { return null }
    if (!isInternal(user) || !hasAppAccess(user)) { return null }
    Long levelId = issue.getSecurityLevelId()
    if (RESTRICTED_SCOPE != 'any-issue') {
        if (levelId == null) { return null }
        // Already on the level: then the level is not what blocks them.
        if (passesSecurity(issue, user)) { return null }
        if (RESTRICTED_SCOPE != 'all') {
            def level = ComponentAccessor.getComponent(IssueSecurityLevelManager).getSecurityLevel(levelId)
            def schemesInScope = SECURED_SCHEMES.collect { it as Long }
            def levelsToSkip   = SECURED_SKIP_LEVELS.collect { it as Long }
            if (level == null || !schemesInScope.contains(level.getSchemeId() as Long) ||
                levelsToSkip.contains(levelId as Long)) { return null }
        }
    }
    String shownKey = ctx.urlKey ?: issue.getKey()
    return [mode: 'restricted', issueKey: shownKey, issueUrl: '/browse/' + shownKey,
            fallbackUrl: escalationFor(ctx.proj)]
}

// modes/missing.groovy -- the key in the URL resolves to no issue: say so.
// Companion of modes/restricted: together they let the audience tell a
// restricted issue from a deleted or mistyped key, which the generic card
// deliberately does not. OFF unless 'missing' is in MODE_ORDER.
//
// It answers ONLY when the 'restricted' mode is built into this file AND
// listed in MODE_ORDER AND RESTRICTED_SCOPE is 'any-issue'. At any narrower
// scope, or without 'restricted', an existing issue outside the scope gets
// the generic card, and a "missing" card next to it would confirm that every
// generic key exists: the scope would then hide the wording, not the fact.
// So in every other configuration this mode stays silent and a missing key
// keeps the generic card, exactly like an issue outside the scope.
//
// Audience: the viewer passes the internal-viewer policy and holds
// application access; checked first, so that the audience gate is exercised
// in every build, whatever the scope. Everyone else keeps the generic card,
// which reads the same whether the key exists or not.
// Reveals: that no issue has this key.

MODES['missing'] = { ctx ->
    if (ctx.issue != null || !ctx.key) { return null }
    if (!isInternal(ctx.user) || !hasAppAccess(ctx.user)) { return null }
    if (!(MODES['restricted'] != null && MODE_ORDER.contains('restricted') && RESTRICTED_SCOPE == 'any-issue')) {
        return null
    }
    return [mode: 'missing', issueKey: ctx.key, helpCenter: HELP_CENTER, fallbackUrl: FALLBACK_URL]
}

// core/decide.groovy -- decides which card (if any) this user gets for this
// issue on this page. Always built in, last in the DECIDE section.

// null = the page renders fine for them -> stay out of the way.
// key is the canonical key of the resolved issue (or the URL's key when no
// issue resolves); urlKey is the key as the viewer typed it, which differs
// after a move. Modes that must not reveal a move use ctx.urlKey.
def decide = { user, issue, String key, String pageKind, String urlKey = null ->
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
    def ctx = [user: user, issue: issue, key: key, urlKey: urlKey ?: key, pageKind: pageKind,
               proj: proj, pm: pm, BROWSE: BROWSE]

    // First mode in MODE_ORDER that answers wins. A mode that is not in this
    // build is skipped; a mode that throws is treated as "no answer".
    for (String name : MODE_ORDER) {
        def answer = null
        try {
            def mode = MODES[name] as Closure
            if (mode == null) { continue }
            answer = mode(ctx)
        } catch (Throwable modeFailed) {
            answer = null
            if (MODE_ERRORS != null) { MODE_ERRORS.add(name + ': ' + modeFailed) }
        }
        if (answer instanceof Map && answer.mode) {
            payload = answer
            break
        }
    }
    return payload
}

// <<< DECIDE
// <<< COPY DECIDE

// Same accounts and keys as tests/decision_test.groovy. needsLevel proves the
// issue still carries a level, so generic really comes from the kill switch.
def CASES = [
  [name: 'F01 SECURED_CARD=false: restricted issue with field and helpers -> generic',
   user: 'alice@example.com', key: 'DEMO-101', page: 'browse', needsLevel: true, expect: [mode: 'generic']],
  [name: 'F02 SECURED_CARD=false: restricted issue without a field -> generic',
   user: 'alice@example.com', key: 'DEMO-103', page: 'browse', needsLevel: true, expect: [mode: 'generic']],
  [name: 'F03 SECURED_CARD=false: portal request, agent page -> portal, unchanged',
   user: 'alice@example.com', key: 'HELP-201', page: 'agent', expect: [mode: 'portal']],
  [name: 'F04 SECURED_CARD=false: request on a restricted level -> generic',
   user: 'alice@example.com', key: 'HELP-203', page: 'browse', needsLevel: true, expect: [mode: 'generic']],
  [name: 'F05 SECURED_CARD=false: the reporter, who can see the issue -> no card, unchanged',
   user: 'bob@example.com', key: 'DEMO-101', page: 'browse', expect: null],
]

def um = ComponentAccessor.getUserManager()
def im = ComponentAccessor.getIssueManager()
def out = new StringBuilder()
int pass = 0, fail = 0, skip = 0
MODE_ERRORS = []            // decide() records every exception a mode swallowed
CASES.each { c ->
    def u = um.getUserByName(c.user)
    if (u == null) {
        skip++
        out.append('SKIP ' + c.name + '  [no such user: ' + c.user + ']\n')
        return
    }
    def issue = im.getIssueObject(c.key)
    if (!c.containsKey('expect') || !(c.expect == null || (c.expect instanceof Map && c.expect.mode))) {
        fail++
        out.append('FAIL ' + c.name + '  [expect must be null or a map with a mode]\n')
        return
    }
    // A key that does not resolve gives the generic card for every viewer,
    // which would make every case here pass for the wrong reason; so would
    // an issue that lost its level.
    def whys = []
    if (issue == null) { whys << ('no such issue: ' + c.key) }
    else if (c.needsLevel && issue.getSecurityLevelId() == null) { whys << (c.key + ' has no security level') }
    if (whys) {
        skip++
        out.append('SKIP ' + c.name + '  [' + whys.join('; ') + ']\n')
        return
    }
    String key = issue.getKey()
    def got = null
    String err = null
    MODE_ERRORS.clear()
    try {
        got = decide(u, issue, key, c.page)
    } catch (Throwable t) {
        err = t.getClass().getName() + ': ' + t.getMessage()
    }
    def problems = []
    if (err != null) {
        problems << ('threw ' + err)
    } else if (c.expect == null) {
        if (got != null) { problems << ('expected no card, got ' + got) }
    } else if (!(got instanceof Map)) {
        problems << ('expected a card, got ' + got)
    } else {
        (c.expect ?: [:]).each { k, v ->
            if (got[k] != v) { problems << (k + ': expected <' + v + '> got <' + got[k] + '>') }
        }
    }
    MODE_ERRORS.each { problems << ('mode threw: ' + it) }
    if (problems) { fail++ } else { pass++ }
    out.append((problems ? 'FAIL ' : 'PASS ') + c.name + '\n')
    out.append('     got: ' + (got instanceof Map ? JsonOutput.toJson(got) : String.valueOf(got)) + '\n')
    problems.each { out.append('     !! ' + it + '\n') }
}
String verdict = (fail == 0 && skip == 0 && pass > 0) ? 'OK' : 'NOT OK'
return 'RESULT ' + verdict + '  SUMMARY pass=' + pass + ' fail=' + fail + ' skip=' + skip +
       (verdict == 'OK' ? '' : '  (every case must run and pass; a SKIP is not a pass)') + '\n' + out.toString()
