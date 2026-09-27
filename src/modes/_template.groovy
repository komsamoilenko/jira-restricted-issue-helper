// modes/_template.groovy -- copy this file to modes/<name>.groovy to add a
// mode. Not built by any profile (the assembler skips names starting with _).
//
// A mode is one closure in the MODES registry. It receives ctx and returns
// either null ("not my case, try the next mode") or a payload map that the
// browser will render. Everything in the payload reaches the browser, so put
// nothing there that the viewer may not learn. The mode runs only when the
// page is already broken for the viewer, and inside a try: an exception is
// the same as null. docs/EXTENDING.md walks through the whole contract.
//
// ctx has: user (ApplicationUser), issue (Issue or null when the key does not
// exist), key (canonical issue key, or null on a queues page without one),
// pageKind ('browse' | 'agent'), proj (Project or null), pm
// (PermissionManager), BROWSE (the Browse Projects permission key).
// Helpers always present: mailOf(u), hasAppAccess(u), passesSecurity(issue, u),
// isServiceDeskProject(proj), hasRequestType(issue), escalationFor(proj).
// Helpers from policy modules, if built: isInternal(u), peopleFor(ctx),
// jsmInCustomerContext { -> ... }, jsmPortalFor(who, proj, key), isRequest(ctx).
//
// provides: mode:example
// requires: hasAppAccess escalationFor

MODES['example'] = { ctx ->
    // 1. Is this my case? Cheapest checks first.
    if (ctx.issue == null) { return null }

    // 2. Audience gates. Decide who may learn what this card says BEFORE you
    //    compute any of it. Never confirm an issue's existence to a viewer
    //    who fails a gate: they must get exactly the generic card.
    if (!hasAppAccess(ctx.user)) { return null }

    // 3. Content. Only what the viewer can act on.
    return [mode: 'example',
            issueKey: ctx.issue.getKey(),
            issueUrl: '/browse/' + ctx.issue.getKey(),
            fallbackUrl: escalationFor(ctx.proj)]
}

// Then: add 'example' to MODE_ORDER in config/config.groovy, teach the client
// script (render/client.js) how to draw d.mode === 'example', add its texts
// to config/text.en.groovy, add a case to tests/render_test.groovy and to
// tests/decision_test.groovy, list the module in a profile, and rebuild.
