// core/decide.groovy -- decides which card (if any) this user gets for this
// issue on this page. Always built in, last in the DECIDE section.
// provides: decide
// requires: MODES MODE_ERRORS
import com.atlassian.application.api.ApplicationKey
import com.atlassian.jira.application.ApplicationAuthorizationService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.permission.ProjectPermissions

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
