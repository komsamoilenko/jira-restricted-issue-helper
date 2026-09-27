// core/context.groovy -- shared helpers and the mode registry.
// provides: MODES mailOf hasAppAccess passesSecurity isServiceDeskProject hasRequestType escalationFor
// Always built in, first in the DECIDE section. Everything here is read-only.
import com.atlassian.jira.application.ApplicationRoleManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.security.IssueSecurityLevelManager

// The registry. Each mode module adds one entry:
//   MODES['name'] = { ctx -> a payload map, or null for "not my case" }
// core/decide.groovy tries them in MODE_ORDER and takes the first answer.
def MODES = [:]

// A mail address for the copy-ready messages. An account without one falls
// back to its username rather than rendering "add me (null)"; where usernames
// are e-mail addresses, that is still a usable address.
def mailOf = { u ->
    def m = u.getEmailAddress() ?: ''
    return m.contains('@') ? m : u.getName()
}

// Application access: true for an account that holds any licensed application
// role (Jira Software, Jira Core, a Service Management agent seat), false for
// portal-only customers and for anonymous. This is the "is not a portal-only
// customer" gate of the share, moved and secured cards. 1.0.0 asked
// GlobalPermissionKey.USE for the same thing; that key has been deprecated
// since Jira 7.0, and ApplicationRoleManager.hasAnyRole is its documented
// successor (present unchanged from Jira 8.0 to 11.x).
def hasAppAccess = { u ->
    u != null && ComponentAccessor.getComponent(ApplicationRoleManager).hasAnyRole(u)
}

// Does this viewer pass the issue's security level? True when the issue has
// none. getUsersSecurityLevels is documented as "can be null", hence ?: [].
def passesSecurity = { issue, u ->
    Long levelId = issue?.getSecurityLevelId()
    if (levelId == null) { return true }
    def levels = ComponentAccessor.getComponent(IssueSecurityLevelManager).getUsersSecurityLevels(issue, u) ?: []
    return levels.any { it.getId() == levelId }
}

// Service Management project? Gate on the project TYPE, not merely on the
// request-type field: issues moved out of a service desk keep the field, and
// the portal lookup throws for them.
def isServiceDeskProject = { proj ->
    proj != null && proj.getProjectTypeKey()?.getKey() == 'service_desk'
}

// Does the issue carry a Customer Request Type value (RT_FIELD)?
def hasRequestType = { issue ->
    def rtField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(RT_FIELD)
    return issue != null && rtField != null && issue.getCustomFieldValue(rtField) != null
}

// Where "Raise a request" leads for this project. Only for cards that already
// confirm the issue exists; the generic card must keep using FALLBACK_URL.
def escalationFor = { proj ->
    (proj != null ? ESCALATION_BY_PROJECT[proj.getKey()] : null) ?: FALLBACK_URL
}
