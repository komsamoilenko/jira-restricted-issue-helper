// core/entry.groovy -- the fragment's entry point: which page is this, who is
// looking, which issue. Always built in, between DECIDE and RENDER.
// provides: payload pageKind route prefixLinks
// requires: decide
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.web.ExecutingHttpRequest

// >>> ROUTE -- build/sync_tests.py copies this block into tests/route_test.groovy
// Which page is this, and which key does it name. Pure functions of the
// request URI and the context path, so they can be tested without a request.
//   route(uri, cp) -> [pageKind: 'browse' | 'agent' | null, key: 'ABC-1' | null]
// Case-insensitive; the key is then re-read from the resolved issue, because
// Jira serves lowercase keys and a moved issue still answers on its old key.
// A Jira served under a context path (for example /jira) reports URIs that
// start with it: it is stripped before matching, and prefixLinks() puts it
// back on every root-relative link the card carries.
def route = { String rawUri, String cp ->
    String uri = rawUri ?: ''
    String ctxPath = cp ?: ''
    if (ctxPath && (uri == ctxPath || uri.startsWith(ctxPath + '/'))) {
        uri = uri.substring(ctxPath.length())
    }
    // Each matcher is asked exactly once: a Matcher in boolean context calls
    // find(), and a second find() on the same matcher continues after the
    // first match instead of starting over, so it would say "no match".
    String kind = null
    String key  = null
    def mBrowse = (uri =~ '(?i)^/browse/([a-z][a-z0-9_]*-[0-9]+)')
    if (mBrowse.find()) {
        kind = 'browse'
        key  = mBrowse.group(1).toUpperCase()
    } else {
        def mAgent = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?/([a-z][a-z0-9_]*-[0-9]+)$')
        if (mAgent.find()) {
            kind = 'agent'
            key  = mAgent.group(1).toUpperCase()
        } else if (uri ==~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?$') {
            kind = 'agent'
        }
    }
    if (kind && !PAGES.contains(kind)) { kind = null; key = null }
    return [pageKind: kind, key: key]
}

// Puts the context path in front of every root-relative link of a payload.
// Protocol-relative links (//host/...) and links that already carry the
// prefix are left alone.
def prefixLinks = { Map p, String cp ->
    if (p == null || !cp) { return p }
    ['issueUrl', 'portalUrl', 'helpCenter', 'fallbackUrl', 'myRequests'].each { k ->
        def v = p[k]
        if (v instanceof String && v.startsWith('/') && !v.startsWith('//') && !v.startsWith(cp + '/')) {
            p[k] = cp + v
        }
    }
    return p
}
// <<< ROUTE

def payload  = null
String pageKind = null

try {
    // `req` stays untyped on purpose: Jira 11 returns a jakarta.servlet
    // request here, earlier versions a javax.servlet one.
    def req = ExecutingHttpRequest.get()
    String cp = req?.getContextPath() ?: ''
    def r = route(req?.getRequestURI() ?: '', cp)
    pageKind = r.pageKind
    if (pageKind) {
        def key  = r.key
        def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
        if (user) {
            def issue = key ? ComponentAccessor.getIssueManager().getIssueObject(key) : null
            if (issue != null) { key = issue.getKey() }   // canonical key, not the URL's
            payload = prefixLinks(decide(user, issue, key, pageKind), cp)
        }
    }
} catch (Throwable ignored) {
    payload = null          // never break a page over a helper
}
