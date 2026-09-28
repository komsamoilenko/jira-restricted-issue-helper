// modes/missing.groovy -- the key in the URL resolves to no issue: say so.
// Companion of modes/restricted: together they let the audience tell a
// restricted issue from a deleted or mistyped key, which the generic card
// deliberately does not. OFF unless 'missing' is in MODE_ORDER.
//
// Audience: the viewer passes the internal-viewer policy and holds
// application access. Everyone else keeps the generic card, which reads the
// same whether the key exists or not.
// Reveals: that no issue has this key.
// provides: mode:missing
// requires: isInternal hasAppAccess

MODES['missing'] = { ctx ->
    if (ctx.issue != null || !ctx.key) { return null }
    if (!isInternal(ctx.user) || !hasAppAccess(ctx.user)) { return null }
    return [mode: 'missing', issueKey: ctx.key, helpCenter: HELP_CENTER, fallbackUrl: FALLBACK_URL]
}
