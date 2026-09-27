// modes/share.groovy -- a Service Management request the viewer is not on,
// whose reporter can add them as a Request participant with the portal's
// Share button. Runs after portal, so the viewer cannot open it themselves.
// Reveals: that the request exists, the reporter's display name, the portal
// link. Never the reporter's e-mail address.
// Gates: application access (never a portal-only customer), the issue's
// security level (a Share would not help someone the level blocks), a usable
// reporter (active, not the viewer, has a mail address, not a bot) who has
// access to the project's portal.
// provides: mode:share
// requires: isRequest jsmInCustomerContext jsmPortalFor passesSecurity hasAppAccess mailOf escalationFor

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
