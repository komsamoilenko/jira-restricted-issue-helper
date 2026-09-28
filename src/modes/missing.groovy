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
// provides: mode:missing
// requires: isInternal hasAppAccess

MODES['missing'] = { ctx ->
    if (ctx.issue != null || !ctx.key) { return null }
    if (!isInternal(ctx.user) || !hasAppAccess(ctx.user)) { return null }
    if (!(MODES['restricted'] != null && MODE_ORDER.contains('restricted') && RESTRICTED_SCOPE == 'any-issue')) {
        return null
    }
    return [mode: 'missing', issueKey: ctx.key, helpCenter: HELP_CENTER, fallbackUrl: FALLBACK_URL]
}
