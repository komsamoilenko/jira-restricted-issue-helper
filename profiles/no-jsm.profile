# Jira Software or Jira Core without Service Management: the restricted card
# and the generic card only. Links and texts stop mentioning the Help Center
# and requests; "raise a request" becomes the stock Contact Administrators
# form (Administration > System > General configuration > Contact
# administrators form must be on), and the generic card's button leads to the
# dashboard. Point FALLBACK_URL at a help page instead if you have one.
name = no-jsm
modules = internal-mail-domain people-reporter-assignee secured
MODE_ORDER = ['secured']
FALLBACK_URL = '/secure/ContactAdministrators!default.jspa'
HELP_CENTER = '/secure/Dashboard.jspa'
TEXT.openHelpCenter = 'Go to the dashboard'
TEXT.genericText = 'It may have been deleted, or you may not have permission to view it. If someone sent you this link, ask them for access.'
TEXT.genericEscalate = 'Still stuck? Contact the Jira administrators'
TEXT.securedEscalateField = 'Nobody to ask? Contact the Jira administrators'
TEXT.securedEscalateNoField = 'Something else? Contact the Jira administrators'
TEXT.securedLeadNoField = 'There is no field on this issue for letting one more person in. If you need it, ask whoever shared the link with you, or contact the Jira administrators.'
