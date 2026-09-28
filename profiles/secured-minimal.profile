# Same modules as full, but the restricted card says as little as possible:
# no level name, no field name, no people. The viewer learns only that the
# issue is restricted, that whoever can edit it can let one more person in,
# and gets a copy-ready message to send to whoever shared the link. Every
# gate still runs; this only trims what passes them.
name = secured-minimal
modules = internal-mail-domain people-reporter-assignee jsm portal share moved secured
DISCLOSURE = [levelName: false, fieldName: false, people: false]
