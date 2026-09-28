# Every mode: Service Management (portal, share, moved) and the restricted
# card (secured), with the mail-domain internal policy. This is the profile
# the author runs in production. The existence-only modes (restricted,
# missing) are built in but off: add them to MODE_ORDER in CONFIG to use them,
# for example ['portal', 'share', 'moved', 'secured', 'restricted'].
name = full
modules = internal-mail-domain people-reporter-assignee jsm portal share moved secured restricted missing
