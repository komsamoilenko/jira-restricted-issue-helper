// Every piece of text the card shows, in one place. {placeholders} are filled
// in by the card: {key} issue key, {oldKey} its former key, {project} project
// name, {level} security level name, {levelPhrase} levelNamed or
// levelUnnamed, {field} field name, {mail} the viewer's e-mail address, {url}
// a full link, {first} a first name. Values reach the browser as JSON and are
// inserted as plain text, never as HTML. The card joins sentences and
// greetings with a single space.
final Map    TEXT = [
    // shared
    copyLabel            : 'Copy message',
    copied               : 'Copied to clipboard',
    copyFailed           : 'Copy it from the box above',
    greetingNamed        : 'Hi {first},',
    greetingPlain        : 'Hi,',
    messageHeadingThem   : 'Message to send them',
    messageHeading       : 'Message to send',
    raiseRequest         : 'Raise a request',
    openHelpCenter       : 'Open the Help Center',
    openKey              : 'Open {key}',
    // role labels on the people chips of the restricted card
    roleReporter         : 'reporter',
    roleAssignee         : 'assignee',
    roleAnd              : 'and',
    // portal
    portalTitle          : 'This request opens on the customer portal',
    portalText           : 'You do have access to it there. Requests like this one are handled on the portal rather than in this Jira view.',
    portalTextAgent      : 'The page you opened is the agent view, and it needs a Jira Service Management agent licence. You do not need one: this request is already open to you on the customer portal, where you can read it and add comments.',
    portalOpen           : 'Open this request',
    portalMyRequests     : 'See all of my open requests',
    // moved
    movedTitle           : 'This request moved out of the service desk',
    movedFrom            : '{oldKey} was moved into {project}, as',
    movedFromUnknown     : 'It was moved into {project}, as',
    movedAfterLink       : '. It is no longer on the customer portal, so the Help Center will not list it.',
    movedNoAccess        : 'You do not have access to {project} yet, which is why this page is empty. Send {key} to whoever asked you about it, or ask us for access.',
    movedAsk             : 'Ask for access',
    // share
    shareTitle           : 'Ask to be added to this request',
    shareText            : '{key} is a Service Management request you are not on. The person who raised it can add you as a Request participant in two clicks, with the Share button on the portal.',
    shareTextAgent       : '{key} is a Service Management request, and this page is the agent view, which needs an agent licence. You do not need one: being a Request participant is enough to read the request and comment on it. The person who raised it can add you in two clicks, with the Share button on the portal.',
    shareMessage         : 'I am trying to open {key} but I do not have access to it. Could you open {url} and use the Share button to add {mail} as a Request participant? That is enough for me to read the request and comment on it. Thank you.',
    shareEscalate        : 'Reporter unavailable? Raise a request',
    // secured (restricted by an issue security level)
    securedTitle         : 'This issue is restricted',
    levelNamed           : 'the security level "{level}"',
    levelUnnamed         : 'a security level',
    securedLead          : '{key} is protected by {levelPhrase}, so only the people and groups on that level can open it.',
    securedLeadField     : 'You do not have to join them: being added to its "{field}" field opens this one issue to you, and nothing else.',
    securedLeadFieldUnnamed : 'You do not have to join them: this issue has a field for letting one more person in, and being added to it opens this one issue to you, and nothing else.',
    securedLeadMany      : 'Either of the people below can do that in a few seconds.',
    securedLeadOne       : 'The person below can do that in a few seconds.',
    securedLeadNobody    : 'Anyone who can edit the issue can add you, so send the message below to whoever shared the link with you.',
    securedLeadAgent     : 'Once you are added, open it with the link below rather than from a queue: queues also need an agent licence.',
    securedLeadNoField   : 'There is no field on this issue for letting one more person in. If you need it, ask whoever shared the link with you, or raise a request.',
    securedMessage       : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to its "{field}" field? That opens this one issue to me and nothing else. Thank you.',
    securedMessageFieldUnnamed : 'I am trying to open {url}, but it is restricted and I cannot see it. Could you add me ({mail}) to the field on the issue that lets one more person see it? That opens this one issue to me and nothing else. Thank you.',
    securedOpenAgain     : 'Added already? Open {key}',
    securedEscalateField : 'Nobody to ask? Raise a request',
    securedEscalateNoField : 'Something else? Raise a request',
    // generic
    genericTitle         : "You can't view this issue",
    genericTitleAgent    : 'This page is for service desk agents',
    genericText          : 'It may have been deleted, or you may not have permission. If you were looking for a Service Management request, the Help Center lists every request you raised.',
    genericTextAgent     : 'Queues are the Jira Service Management agent view, and only agents can open them. You almost certainly do not need that: requests you raised are all listed in the Help Center, and if you need access to a particular one, or help with anything else, raise a request and we will sort it out.',
    genericEscalate      : 'Still stuck? Raise a request and we will help',
]
