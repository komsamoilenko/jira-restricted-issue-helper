// policy/people-reporter-assignee.groovy -- who the restricted card may name
// as able to add the viewer: the reporter first, then the assignee, each only
// while that person is active, is not the viewer, has a mail address, is not
// an automation account (BOT_NAMES), and can see AND edit this issue.
// provides: peopleFor
// To name someone else (a project lead, a component lead), write another
// module that provides peopleFor with the same shape:
//   [[name: 'Display Name', role: 'reporter'], ...]   at most two entries.
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.permission.ProjectPermissions

def peopleFor = { ctx ->
    def issue = ctx.issue
    def user  = ctx.user
    def pm    = ctx.pm
    def im    = ComponentAccessor.getIssueManager()
    def byName = new LinkedHashMap()
    [[issue.getReporter(), TEXT.roleReporter], [issue.getAssignee(), TEXT.roleAssignee]].each { pr ->
        def u = pr[0]
        if (u == null || u.getName() == user.getName() || !u.isActive()) { return }
        if (!(u.getEmailAddress() ?: '').contains('@')) { return }
        if (BOT_NAMES.contains((u.getDisplayName() ?: '').toLowerCase())) { return }
        if (!byName.containsKey(u.getName())) {
            if (!pm.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, u) ||
                !pm.hasPermission(ProjectPermissions.EDIT_ISSUES, issue, u) ||
                !im.isEditable(issue, u)) { return }
            byName.put(u.getName(), [name: u.getDisplayName(), roles: []])
        }
        byName.get(u.getName()).roles.add(pr[1])
    }
    return byName.values().collect { [name: it.name, role: it.roles.join(' ' + TEXT.roleAnd + ' ')] }
}
