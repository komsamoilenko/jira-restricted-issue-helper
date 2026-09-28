// policy/jsm.groovy -- Jira Service Management services for the portal and
// share modes. The classes are reached by name at run time, so the assembled
// file compiles and runs where Service Management is absent (every call is
// inside a try in the modes that use it, and a failure means "no card").
// provides: jsmInCustomerContext jsmPortalFor isRequest
// requires: isServiceDeskProject hasRequestType
import com.atlassian.jira.component.ComponentAccessor

// Runs the body in the CUSTOMER context, which is how the portal itself
// decides who may open a request. The body is coerced to JSM's
// NoExceptionsCallable with Groovy's asType, which also gives the proxy
// sane equals/hashCode/toString (1.0.0 used a bare java.lang.reflect.Proxy).
def jsmInCustomerContext = { Closure body ->
    def ccs = ComponentAccessor.getOSGiComponentInstanceOfType(
        Class.forName('com.atlassian.servicedesk.api.customer.CustomerContextService'))
    def nec = Class.forName('com.atlassian.servicedesk.api.customer.NoExceptionsCallable')
    return ccs.runInCustomerContext(body.asType(nec))
}

// The portal URL of the request for a given person, or null when that person
// has no portal for the project (getPortalForProject throws rather than
// returning null).
def jsmPortalFor = { who, proj, String key ->
    try {
        def ps = ComponentAccessor.getOSGiComponentInstanceOfType(
            Class.forName('com.atlassian.servicedesk.api.portal.PortalService'))
        def portal = ps.getPortalForProject(who, proj)
        return portal ? ('/servicedesk/customer/portal/' + portal.getId() + '/' + key) : null
    } catch (Throwable ignoredInner) {
        return null
    }
}

// A current Service Management request: a service-desk project AND a value in
// the request-type field.
def isRequest = { ctx ->
    isServiceDeskProject(ctx.proj) && hasRequestType(ctx.issue)
}
