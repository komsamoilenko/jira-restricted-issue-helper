// ============================================================================
//  Compare two versions of decide() on the same data        READ-ONLY
// ----------------------------------------------------------------------------
//  A release that promises "the same card as before" should prove it. This
//  template runs the DECIDE section of two assembled files, OLD and NEW, over
//  the same (viewer, issue, page) triples and lists every difference.
//
//  It is NOT refreshed by build/sync_tests.py: paste the sections by hand.
//   1. Put the IMPORTS of both versions at the top (duplicates are fine).
//   2. Paste OLD's CONFIG and DECIDE sections, with your CONFIG values,
//      inside the V_OLD closure below, and NEW's inside V_NEW. Each closure
//      returns its own decide(); the closures keep the two copies' script
//      variables apart, so both can define MODES, isInternal and so on.
//   3. Fill USERS, KEYS and PAGES. Keep the run under about thirty seconds
//      per paste: a load balancer may answer 504 to a longer Script Console
//      run even though the script keeps going on the server. Split the
//      sample if needed.
//   4. Run. RESULT OK means no difference in any payload key except those
//      listed in IGNORE (new keys the old version cannot know about).
//
//  Everything is read-only: decide() only reads.
// ============================================================================
import com.atlassian.jira.component.ComponentAccessor
import groovy.json.JsonOutput
// ---- add the import lines of BOTH versions here ----

def V_OLD = { ->
    // ---- paste OLD: // >>> CONFIG ... // <<< CONFIG, then // >>> DECIDE ... // <<< DECIDE ----
    return decide
}()

def V_NEW = { ->
    // ---- paste NEW: // >>> CONFIG ... // <<< CONFIG, then // >>> DECIDE ... // <<< DECIDE ----
    return decide
}()

// Keys the old version does not produce; ignored in the comparison.
def IGNORE = ['hasField'] as Set

def USERS = ['alice@example.com', 'customer@example.org', 'contractor@example.org']
def KEYS  = ['DEMO-101', 'DEMO-150', 'HELP-201', 'HELP-202', 'HELP-203', 'NOPE-99999']
def PAGES = ['browse', 'agent']

def um = ComponentAccessor.getUserManager()
def im = ComponentAccessor.getIssueManager()
def out = new StringBuilder()
int same = 0, diff = 0, skipped = 0
def strip = { p -> p == null ? null : p.findAll { k, v -> !(k in IGNORE) } }
USERS.each { name ->
    def u = um.getUserByName(name)
    if (u == null) { skipped++; out.append('SKIP no such user: ' + name + '\n'); return }
    KEYS.each { k ->
        def issue = im.getIssueObject(k)
        String key = issue != null ? issue.getKey() : k
        PAGES.each { page ->
            def a = null, b = null
            String ea = null, eb = null
            try { a = V_OLD(u, issue, key, page) } catch (Throwable t) { ea = t.toString() }
            try { b = V_NEW(u, issue, key, page) } catch (Throwable t) { eb = t.toString() }
            boolean ok = ea == null && eb == null && strip(a) == strip(b)
            if (ok) { same++ } else {
                diff++
                out.append('DIFF ' + name + ' x ' + key + ' / ' + page + '\n')
                out.append('     old: ' + (ea ?: JsonOutput.toJson(a)) + '\n')
                out.append('     new: ' + (eb ?: JsonOutput.toJson(b)) + '\n')
            }
        }
    }
}
String verdict = (diff == 0 && skipped == 0 && same > 0) ? 'OK' : 'NOT OK'
return 'RESULT ' + verdict + '  same=' + same + ' diff=' + diff + ' skipped=' + skipped + '\n' + out.toString()
