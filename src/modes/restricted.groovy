// modes/restricted.groovy -- existence only. The issue exists and this viewer
// may not see it: say that, and nothing else. No level name, no field, no
// people, no copy-ready message. For instances that do not want the card to
// say anything about a restricted issue, but want staff to tell "restricted"
// from "deleted" (its companion, modes/missing, covers the other half).
//
// OFF unless 'restricted' is in MODE_ORDER. Put it after 'secured' to use it
// as the fallback when the restricted card has nothing safe to say, or alone
// (profile exists-only). It deliberately relaxes the rule that a viewer who
// fails the gates cannot tell a hidden issue from a missing key: for the
// audience below, it can. docs/DESIGN.md, "Existence only".
//
// Audience: the viewer passes the internal-viewer policy and holds
// application access. Scope, by RESTRICTED_SCOPE:
//   'in-scope'   only levels of SECURED_SCHEMES minus SECURED_SKIP_LEVELS, so
//                issues in compartments whose existence is the secret stay
//                indistinguishable from missing keys (default);
//   'all'        every issue hidden by a security level;
//   'any-issue'  every issue the viewer cannot browse, level or not.
// Reveals: that the issue exists and the viewer lacks permission. The key is
// already in the URL.
// provides: mode:restricted
// requires: isInternal hasAppAccess passesSecurity escalationFor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.security.IssueSecurityLevelManager

MODES['restricted'] = { ctx ->
    def issue = ctx.issue
    def user  = ctx.user
    if (issue == null) { return null }
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
    return [mode: 'restricted', issueKey: issue.getKey(), issueUrl: '/browse/' + issue.getKey(),
            fallbackUrl: escalationFor(ctx.proj)]
}
