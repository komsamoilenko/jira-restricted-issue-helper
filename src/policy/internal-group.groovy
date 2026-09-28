// policy/internal-group.groovy -- who counts as internal: membership of one of
// the groups in INTERNAL_GROUPS. Use it where usernames are not e-mail
// addresses (a directory login, say) and a group is maintained to equal staff
// exactly. See docs/DESIGN.md, "Who counts as internal".
// provides: isInternal
// Alternative: policy/internal-mail-domain.groovy. Build exactly one of them.
import com.atlassian.jira.component.ComponentAccessor

def isInternal = { u ->
    if (u == null || INTERNAL_GROUPS.isEmpty()) { return false }
    def gm = ComponentAccessor.getGroupManager()
    return INTERNAL_GROUPS.any { g -> gm.isUserInGroup(u, g as String) }
}
