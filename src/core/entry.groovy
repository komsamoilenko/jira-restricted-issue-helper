// core/entry.groovy -- the fragment's entry point: which page is this, who is
// looking, which issue. Always built in, between DECIDE and RENDER.
// provides: payload pageKind
// requires: decide
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.web.ExecutingHttpRequest

def payload  = null
String pageKind = null

try {
    // Case-insensitive, and the key is then taken from the resolved issue,
    // not from the URL: Jira serves lowercase keys, and a moved issue still
    // answers on its old key. `req` stays untyped on purpose: Jira 11 returns
    // a jakarta.servlet request here, earlier versions a javax.servlet one.
    def req = ExecutingHttpRequest.get()
    def uri = req?.getRequestURI() ?: ''
    def mBrowse = (uri =~ '(?i)^/browse/([a-z][a-z0-9_]*-[0-9]+)')
    def mAgent  = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?/([a-z][a-z0-9_]*-[0-9]+)$')
    def mQueues = (uri =~ '(?i)^/projects/[a-z0-9_]+/queues(?:/.*)?$')
    pageKind = mBrowse ? 'browse' : ((mAgent || mQueues) ? 'agent' : null)
    if (pageKind && PAGES.contains(pageKind)) {
        def key  = mBrowse ? mBrowse[0][1].toUpperCase()
                           : (mAgent ? mAgent[0][1].toUpperCase() : null)
        def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
        if (user) {
            def issue = key ? ComponentAccessor.getIssueManager().getIssueObject(key) : null
            if (issue != null) { key = issue.getKey() }   // canonical key, not the URL's
            payload = decide(user, issue, key, pageKind)
        }
    } else {
        pageKind = null
    }
} catch (Throwable ignored) {
    payload = null          // never break a page over a helper
}
