# Existence only: the card says whether the issue exists and is closed to the
# viewer, or does not exist at all, and nothing else. No level names, no
# fields, no people, no Service Management cards. For instances that do not
# want to share anything about a restricted issue, but want staff to tell
# "restricted" from "deleted or mistyped".
#
# Audience: internal viewers (mail-domain policy) with application access;
# everyone else keeps the generic card, which reads the same either way.
# RESTRICTED_SCOPE = 'all' confirms every level-protected issue; set it to
# 'in-scope' to keep compartments listed in SECURED_SKIP_LEVELS (or schemes
# outside SECURED_SCHEMES) indistinguishable from missing keys, or to
# 'any-issue' to cover issues the viewer cannot browse for any reason.
# Note that the audience can then tell which keys exist, so the profile is
# meant for staff, never for customers.
name = exists-only
modules = internal-mail-domain restricted missing
MODE_ORDER = ['restricted', 'missing']
RESTRICTED_SCOPE = 'all'
