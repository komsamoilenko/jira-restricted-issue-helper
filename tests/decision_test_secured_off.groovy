// ============================================================================
//  Kill-switch test for src/browse-error-helper.groovy     READ-ONLY
// ----------------------------------------------------------------------------
//  The same decide() as tests/decision_test.groovy, with SECURED_CARD = false
//  (build/sync_tests.py flips it in the CONFIG copy). Proves that switching
//  the restricted card off restores the generic card for every viewer it
//  covered, and leaves the other modes alone. Uses the synthetic accounts and
//  issues described in tests/decision_test.groovy; replace them the same way.
//  Paste the whole file into ScriptRunner > Console and run it.
// ============================================================================
// >>> COPY IMPORTS
import com.atlassian.application.api.ApplicationKey
import com.atlassian.jira.application.ApplicationAuthorizationService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.operation.IssueOperations
import com.atlassian.jira.issue.security.IssueSecurityLevelManager
import com.atlassian.jira.issue.security.IssueSecuritySchemeManager
import com.atlassian.jira.permission.GlobalPermissionKey
import com.atlassian.jira.security.plugin.ProjectPermissionKey
import com.atlassian.jira.web.ExecutingHttpRequest
import groovy.json.JsonOutput
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
// <<< COPY IMPORTS
// >>> COPY CONFIG_SECURED_OFF
// >>> CONFIG =================================================================
//  Every deployment-specific value lives between this line and "<<< CONFIG".
//  Nothing else in this file needs editing; docs/CONFIG.md explains each one.
//  The defaults are PLACEHOLDERS: until you replace them, the restricted card
//  never fires and "Raise a request" points at a request type that does not
//  exist on your instance.

// Where "Raise a request" and "Ask for access" lead: the create page of the
// Service Management request type that handles access questions.
// Format: /servicedesk/customer/portal/<portal id>/create/<request type id>
final String FALLBACK_URL = '/servicedesk/customer/portal/1/create/1'

// The customer portal's Help Center (stock Jira Service Management path).
final String HELP_CENTER  = '/servicedesk/customer/portals'

// The viewer's own open requests on the customer portal (stock path).
final String MY_REQUESTS  = '/servicedesk/customer/user/requests?status=open'

// Id of the Service Management "Customer Request Type" custom field on your
// instance (Administration > Issues > Custom fields; the numeric id is in the
// field's configure link). An unknown id is harmless: the portal and share
// modes stop applying, and moved-issue detection falls back to key history.
final String RT_FIELD     = 'customfield_12345'

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

// Internal-viewer policy: who may be told that a restricted issue exists.
// Each entry is a regular expression matched against the WHOLE domain part
// (after the "@", lower-cased) of an address: 'example\\.com' matches
// someone@example.com only, 'example\\.[a-z]+' matches any top-level domain.
// An empty list means nobody is internal, so the restricted card never fires.
final List   INTERNAL_MAIL_DOMAINS = ['example\\.com']

// true  = the USERNAME must match INTERNAL_MAIL_DOMAINS as well as the e-mail
//         address. Use this where usernames are e-mail addresses that only
//         administrators can change: a user who edits their own e-mail
//         address still cannot pass.
// false = the e-mail address alone decides. Choose this only where users
//         cannot change their own e-mail address.
final boolean INTERNAL_REQUIRE_USERNAME = true

// Issue security SCHEMES whose levels may get the restricted card; levels of
// every other scheme keep the generic card. Keep the L suffix: getSchemeId()
// is a Long, and [12345].contains(12345L) is false, which would silently
// switch the card off everywhere.
final List   SECURED_SCHEMES     = [12345L]

// Levels inside those schemes that must never get the card, because their
// NAME or their MEMBERS are what the level protects (compartments for people
// matters, for example). Level ids, with the L suffix.
final List   SECURED_SKIP_LEVELS = [12346L, 12347L]

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
    securedLeadMany      : 'Either of the people below can do that in a few seconds.',
    securedLeadOne       : 'The person below can do that in a few seconds.',
    securedLeadNobody    : 'Anyone who can edit the issue can add you, so send the message below to whoever shared the link with you.',
    securedLeadAgent     : 'Once you are added, open it with the link below rather than from a queue: queues also need an agent licence.',
    securedLeadNoField   : 'There is no field on this issue for letting one more person in. If you need it, ask whoever shared the link with you, or raise a request.',
    securedMessage       : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to its "{field}" field? That opens this one issue to me and nothing else. Thank you.',
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
// <<< COPY CONFIG_SECURED_OFF
// >>> COPY DECIDE
// >>> DECIDE -- build/sync_tests.py copies everything from here down to
//               "<<< DECIDE" verbatim into tests/decision_test*.groovy, so
//               the decision tests run the deployed logic, not a copy of it.

// A mail address for the copy-ready messages. An account without one falls
// back to its username rather than rendering "add me (null)"; where usernames
// are e-mail addresses, that is still a usable address.
def mailOf = { u ->
    def m = u.getEmailAddress() ?: ''
    return m.contains('@') ? m : u.getName()
}

// Internal-viewer policy (CONFIG: INTERNAL_MAIL_DOMAINS and
// INTERNAL_REQUIRE_USERNAME). An address qualifies when it holds exactly one
// "@" and its whole domain matches one of the patterns.
def internalAddr = { String a ->
    def m = (a ?: '').toLowerCase()
    int at = m.lastIndexOf('@')
    return at > 0 && m.indexOf('@') == at &&
           INTERNAL_MAIL_DOMAINS.any { p -> m.substring(at + 1) ==~ p }
}
def isInternal = { u ->
    (!INTERNAL_REQUIRE_USERNAME || internalAddr(u.getName())) && internalAddr(u.getEmailAddress())
}

// The issue is hidden by its SECURITY LEVEL, and the level is the only thing
// stopping this viewer. Points at the level's own "add one person to this one
// issue" field and names who can fill it in. Returns null whenever any gate
// fails (see the header), so the caller keeps its generic card.
def securedPayload = { user, issue ->
    Long levelId = issue.getSecurityLevelId()
    if (levelId == null) { return null }
    // Cheapest gates first. Portal-only customers must never be told an issue
    // exists, and neither must anyone the internal-viewer policy rejects.
    if (!isInternal(user)) { return null }
    if (!ComponentAccessor.getGlobalPermissionManager().hasPermission(GlobalPermissionKey.USE, user)) {
        return null
    }
    def islm  = ComponentAccessor.getComponent(IssueSecurityLevelManager)
    def level = islm.getSecurityLevel(levelId)
    if (level == null || !SECURED_SCHEMES.contains(level.getSchemeId() as Long) ||
        SECURED_SKIP_LEVELS.contains(levelId as Long)) { return null }
    if (islm.getUsersSecurityLevels(issue, user).any { it.getId() == levelId }) { return null }

    def proj = issue.getProjectObject()
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
    // candidate at all.
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
        field = candidates.findAll { onEdit.contains(it.getId()) }
                          .sort { it.getIdAsLong() }
                          .find { true }
    }

    // Would the viewer actually get in? Issue security is a second lock on top
    // of the permission scheme. Project-level Browse is NOT the test: it is
    // true for everyone on projects that grant Browse through the reporter or
    // a user field. hasSchemePermission consults the permission scheme only
    // (issue security is a separate check that PermissionManager adds on
    // top), so it answers "would the scheme let this viewer browse THIS
    // issue"; its last argument, issueCreation, is false because the issue
    // exists. Failing that, accept a scheme that grants Browse through the
    // very field we suggest.
    def pm     = ComponentAccessor.getPermissionManager()
    def BROWSE = new ProjectPermissionKey('BROWSE_PROJECTS')
    def psm    = ComponentAccessor.getPermissionSchemeManager()
    boolean schemeLetsIn = psm.hasSchemePermission(BROWSE, issue, user, false)
    if (!schemeLetsIn && field != null) {
        schemeLetsIn = psm.getPermissionSchemeEntries(psm.getSchemeFor(proj), BROWSE)
                          .any { it.getType() == 'userCF' && it.getParameter() == field.getId() }
    }
    if (!schemeLetsIn) { return null }

    // Who can actually add them: reporter first, then assignee, and only while
    // that person is active, has a mail address, is not an automation
    // account, and can see AND edit this issue.
    def people = []
    if (field != null) {
        def EDIT  = new ProjectPermissionKey('EDIT_ISSUES')
        def byName = new LinkedHashMap()
        [[issue.getReporter(), TEXT.roleReporter], [issue.getAssignee(), TEXT.roleAssignee]].each { pr ->
            def u = pr[0]
            if (u == null || u.getName() == user.getName() || !u.isActive()) { return }
            if (!(u.getEmailAddress() ?: '').contains('@')) { return }
            if (BOT_NAMES.contains((u.getDisplayName() ?: '').toLowerCase())) { return }
            if (!byName.containsKey(u.getName())) {
                if (!pm.hasPermission(BROWSE, issue, u) || !pm.hasPermission(EDIT, issue, u) ||
                    !im.isEditable(issue, u)) { return }
                byName.put(u.getName(), [name: u.getDisplayName(), roles: []])
            }
            byName.get(u.getName()).roles.add(pr[1])
        }
        people = byName.values().collect { [name: it.name, role: it.roles.join(' ' + TEXT.roleAnd + ' ')] }
    }

    return [mode: 'secured', issueKey: issue.getKey(), issueUrl: '/browse/' + issue.getKey(),
            levelName: (level.getName() ?: '').trim(),
            fieldName: field?.getName(), people: people,
            myMail: mailOf(user), fallbackUrl: FALLBACK_URL]
}

// Decides which card (if any) this user gets for this issue on this page.
// null = the page renders fine for them -> stay out of the way.
def decide = { user, issue, String key, String pageKind ->
    def pm     = ComponentAccessor.getPermissionManager()
    def BROWSE = new ProjectPermissionKey('BROWSE_PROJECTS')

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

    // Safe default from here on: any failure below degrades to this
    // card rather than blanking the helper.
    def payload = [mode: 'generic', helpCenter: HELP_CENTER, fallbackUrl: FALLBACK_URL]

    try {
        def proj = issue?.getProjectObject()
        // Gate on the project TYPE, not merely on the request-type field:
        // issues moved out of a service desk keep the field, and the portal
        // lookup throws for them.
        boolean isServiceDesk = proj != null &&
            proj.getProjectTypeKey()?.getKey() == 'service_desk'
        def rtField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(RT_FIELD)
        boolean isRequest = isServiceDesk && rtField != null &&
            issue.getCustomFieldValue(rtField) != null

        if (isRequest) {
            // Permission checks run in the CUSTOMER context, which is how the
            // portal itself decides; the service is reached reflectively so
            // the fragment still compiles where Service Management is absent.
            def ccs = ComponentAccessor.getOSGiComponentInstanceOfType(
                Class.forName('com.atlassian.servicedesk.api.customer.CustomerContextService'))
            def nec = Class.forName('com.atlassian.servicedesk.api.customer.NoExceptionsCallable')
            def inCustomerContext = { Closure body ->
                def proxy = Proxy.newProxyInstance(nec.getClassLoader(), [nec] as Class[],
                    { p, m, a -> m.getName() == 'call' ? body() : null } as InvocationHandler)
                return ccs.runInCustomerContext(proxy)
            }
            def portalFor = { who ->
                try {
                    def ps = ComponentAccessor.getOSGiComponentInstanceOfType(
                        Class.forName('com.atlassian.servicedesk.api.portal.PortalService'))
                    def portal = ps.getPortalForProject(who, proj)
                    return portal ? ('/servicedesk/customer/portal/' + portal.getId() + '/' + key) : null
                } catch (Throwable ignoredInner) {
                    return null
                }
            }

            def mine = inCustomerContext {
                pm.hasPermission(BROWSE, issue, user) ? portalFor(user) : null
            }

            if (mine) {
                payload = [mode: 'portal', portalUrl: mine,
                           myRequests: MY_REQUESTS, fallbackUrl: FALLBACK_URL]
            } else {
                // Would a Share by the reporter actually unblock them?
                boolean passesSecurity = true
                if (issue.getSecurityLevelId() != null) {
                    def islm = ComponentAccessor.getComponent(IssueSecurityLevelManager)
                    passesSecurity = islm.getUsersSecurityLevels(issue, user)
                                         .any { it.getId() == issue.getSecurityLevelId() }
                }
                // Portal-only customers must never be told an issue exists.
                boolean viewerIsInternal = ComponentAccessor.getGlobalPermissionManager()
                                              .hasPermission(GlobalPermissionKey.USE, user)
                def rep = issue.getReporter()
                boolean repUsable = rep != null && rep.isActive() &&
                    rep.getName() != user.getName() &&
                    (rep.getEmailAddress() ?: '').contains('@') &&
                    !BOT_NAMES.contains((rep.getDisplayName() ?: '').toLowerCase())

                if (passesSecurity && viewerIsInternal && repUsable) {
                    def viaReporter = inCustomerContext { portalFor(rep) }
                    if (viaReporter) {
                        payload = [mode: 'share', issueKey: key, portalUrl: viaReporter,
                                   reporterName: rep.getDisplayName(),
                                   myMail: mailOf(user),
                                   fallbackUrl: FALLBACK_URL]
                    }
                }
            }
        } else if (proj != null && !isServiceDesk) {
            // ---- the issue was MOVED OUT of a service desk -----------------
            // Its portal URL is dead for everyone, and the Help Center will
            // never list it again, so the generic card's "the Help Center
            // lists every request you raised" is actively wrong here.
            def prevSd = []
            try {
                def sdSet = [] as Set
                ComponentAccessor.getProjectManager().getProjectObjects().each { p ->
                    if (p.getProjectTypeKey()?.getKey() == 'service_desk') {
                        sdSet.add(p.getKey())
                    }
                }
                prevSd = (ComponentAccessor.getIssueManager()
                              .getAllIssueKeys(issue.getId()) as Set)
                         .findAll { it != issue.getKey() &&
                                    sdSet.contains(it.tokenize('-')[0]) }
                         .toList()
            } catch (Throwable ignoredKeys) { }

            // Key history is the reliable signal: it survives the destination
            // project dropping the request-type field, which a move does not
            // always keep. The field is kept as the fallback, because key
            // history misses issues whose SOURCE project key changed later.
            boolean wasRequest = !prevSd.isEmpty() ||
                (rtField != null && issue.getCustomFieldValue(rtField) != null)

            if (MOVED_CARD && wasRequest) {
                // Same two gates as `share`: never confirm an issue's
                // existence to a portal-only customer, never bypass issue
                // security. Everyone else keeps the generic card.
                boolean passesSecurity = true
                if (issue.getSecurityLevelId() != null) {
                    def islm = ComponentAccessor.getComponent(IssueSecurityLevelManager)
                    passesSecurity = islm.getUsersSecurityLevels(issue, user)
                                         .any { it.getId() == issue.getSecurityLevelId() }
                }
                boolean viewerIsInternal = ComponentAccessor.getGlobalPermissionManager()
                                              .hasPermission(GlobalPermissionKey.USE, user)
                if (passesSecurity && viewerIsInternal) {
                    payload = [mode: 'moved',
                               issueKey: issue.getKey(),
                               issueUrl: '/browse/' + issue.getKey(),
                               projectName: proj.getName(),
                               oldKey: prevSd.isEmpty() ? null : prevSd[0],
                               fallbackUrl: FALLBACK_URL]
                }
            }
        }
    } catch (Throwable ignoredJsm) {
        // keep the generic card
    }

    // ---- hidden by the security level --------------------------------------
    // Only replaces the GENERIC card: portal/share/moved already answer the
    // viewers they apply to, and none of them fires for someone the level
    // blocks.
    if (SECURED_CARD && issue != null && payload.mode == 'generic') {
        try {
            def sec = securedPayload(user, issue)
            if (sec != null) { payload = sec }
        } catch (Throwable ignoredSecured) {
            // keep the generic card
        }
    }
    return payload
}
// <<< DECIDE
// <<< COPY DECIDE

def CASES = [
  [name: 'F01 SECURED_CARD=false: restricted issue with field and helpers -> generic',
   user: 'alice@example.com', key: 'DEMO-101', page: 'browse', expect: [mode: 'generic']],
  [name: 'F02 SECURED_CARD=false: restricted issue without a field -> generic',
   user: 'alice@example.com', key: 'DEMO-103', page: 'browse', expect: [mode: 'generic']],
  [name: 'F03 SECURED_CARD=false: portal request, agent page -> portal, unchanged',
   user: 'alice@example.com', key: 'HELP-201', page: 'agent', expect: [mode: 'portal']],
  [name: 'F04 SECURED_CARD=false: request on a restricted level -> generic',
   user: 'alice@example.com', key: 'HELP-203', page: 'browse', expect: [mode: 'generic']],
]

def um = ComponentAccessor.getUserManager()
def im = ComponentAccessor.getIssueManager()
def out = new StringBuilder()
int pass = 0, fail = 0, skip = 0
CASES.each { c ->
    def u = um.getUserByName(c.user)
    if (u == null) {
        skip++
        out.append('SKIP ' + c.name + '  [no such user: ' + c.user + ']\n')
        return
    }
    def issue = im.getIssueObject(c.key)
    String key = issue != null ? issue.getKey() : c.key
    def got = null
    String err = null
    try {
        got = decide(u, issue, key, c.page)
    } catch (Throwable t) {
        err = t.getClass().getName() + ': ' + t.getMessage()
    }
    def problems = []
    if (err != null) {
        problems << ('threw ' + err)
    } else if (!(got instanceof Map)) {
        problems << ('expected a card, got ' + got)
    } else {
        (c.expect ?: [:]).each { k, v ->
            if (got[k] != v) { problems << (k + ': expected <' + v + '> got <' + got[k] + '>') }
        }
    }
    if (problems) { fail++ } else { pass++ }
    out.append((problems ? 'FAIL ' : 'PASS ') + c.name + '\n')
    out.append('     got: ' + (got instanceof Map ? JsonOutput.toJson(got) : String.valueOf(got)) + '\n')
    problems.each { out.append('     !! ' + it + '\n') }
}
return 'SUMMARY pass=' + pass + ' fail=' + fail + ' skip=' + skip + '\n' + out.toString()
