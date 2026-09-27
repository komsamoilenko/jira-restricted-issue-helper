// modes/moved.groovy -- the issue USED to be a Service Management request and
// was moved out of the service desk. Its portal URL is dead for everyone and
// the Help Center will never list it again, so the generic card's "the Help
// Center lists every request you raised" would be actively wrong here.
// Reveals: that the issue exists, its current key and project name, its
// former key.
// Gates: application access (never a portal-only customer), the issue's
// security level. Kill switch: MOVED_CARD.
// provides: mode:moved
// requires: isServiceDeskProject hasRequestType passesSecurity hasAppAccess escalationFor
import com.atlassian.jira.component.ComponentAccessor

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
