// policy/internal-mail-domain.groovy -- who counts as internal: e-mail domain,
// and by default the username as well (CONFIG: INTERNAL_MAIL_DOMAINS,
// INTERNAL_REQUIRE_USERNAME). See docs/DESIGN.md, "Who counts as internal".
// provides: isInternal
// Alternative: policy/internal-group.groovy. Build exactly one of them.

// An address qualifies when it holds exactly one "@" and its whole domain
// matches one of the patterns.
def internalAddr = { String a ->
    def m = (a ?: '').toLowerCase()
    int at = m.lastIndexOf('@')
    return at > 0 && m.indexOf('@') == at &&
           INTERNAL_MAIL_DOMAINS.any { p -> m.substring(at + 1) ==~ p }
}

def isInternal = { u ->
    u != null &&
    (!INTERNAL_REQUIRE_USERNAME || internalAddr(u.getName())) && internalAddr(u.getEmailAddress())
}
