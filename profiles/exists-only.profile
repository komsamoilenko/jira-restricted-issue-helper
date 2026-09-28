# Existence only: the card says whether the issue exists and is closed to the
# viewer, or does not exist at all, and nothing else. No level names, no
# fields, no people, no Service Management cards. For instances that do not
# want to share anything about a restricted issue, but want staff to tell
# "restricted" from "deleted or mistyped".
#
# Audience: internal viewers (mail-domain policy) with application access;
# everyone else keeps the generic card, which reads the same either way.
# RESTRICTED_SCOPE must be 'any-issue' here: it is the only scope at which the
# 'missing' card answers, because at a narrower scope an existing issue
# outside the scope would get the generic card and the "missing" card next to
# it would confirm that every generic key exists. So this profile confirms the
# existence of EVERY issue the viewer cannot browse, on purpose, and the
# audience can tell which keys exist, including by trying keys. Meant for
# staff, never for customers. To keep some compartments unconfirmed, use the
# full profile with 'restricted' added to MODE_ORDER and RESTRICTED_SCOPE
# 'in-scope' instead: then no "missing" card exists.
name = exists-only
modules = internal-mail-domain restricted missing
MODE_ORDER = ['restricted', 'missing']
RESTRICTED_SCOPE = 'any-issue'
