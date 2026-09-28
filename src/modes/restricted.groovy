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
// provides: mode:restricted
// requires: isInternal hasAppAccess passesSecurity escalationFor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.security.IssueSecurityLevelManager

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
