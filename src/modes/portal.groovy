// modes/portal.groovy -- a Service Management request the viewer CAN open on
// the customer portal: point them there instead of at the agent view.
// Reveals: a link to a request the viewer can already open.
// provides: mode:portal
// requires: isRequest jsmInCustomerContext jsmPortalFor escalationFor

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
