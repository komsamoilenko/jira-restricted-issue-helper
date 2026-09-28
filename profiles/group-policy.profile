# Same as full, with "internal" defined by group membership (INTERNAL_GROUPS)
# instead of e-mail domain. For directories where usernames are logins rather
# than e-mail addresses, and a group is maintained to equal staff exactly.
name = group-policy
modules = internal-group people-reporter-assignee jsm portal share moved secured
INTERNAL_GROUPS = ['jira-staff']
