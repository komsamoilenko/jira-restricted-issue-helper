// modes/secured.groovy -- the issue is hidden by its SECURITY LEVEL, and the
// level is the only thing stopping this viewer. Says so, points at the
// level's own "add one person to this one issue" field, and names who can
// fill it in. Every gate below must hold; the first failure returns null and
// the caller keeps its generic card (docs/DESIGN.md, "The restricted card's
// gates, in order"). DISCLOSURE and DISCLOSURE_BY_LEVEL trim what the card
// says once the gates have passed; they never widen the audience.
// provides: mode:secured
// requires: isInternal hasAppAccess passesSecurity peopleFor mailOf escalationFor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.operation.IssueOperations
import com.atlassian.jira.issue.security.IssueSecurityLevelManager
import com.atlassian.jira.issue.security.IssueSecuritySchemeManager

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
