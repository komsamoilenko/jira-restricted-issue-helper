# Build tools

Three small Python scripts (3.10 or later, standard library only). None of them talks to Jira.

| Script | What it does |
|---|---|
| `assemble.py` | joins a profile's modules from `src/` into one deployable file, `dist/<profile>.groovy` |
| `strip_client_comments.py` | checks, and if asked strips, comments in the client script block of a fragment file |
| `sync_tests.py` | copies the sections of an assembled file into the Script Console tests |

You need them only if you change `src/` or a profile, or want to refresh the tests with your values. Installing one of the committed files in `dist/` needs no Python at all.

## assemble.py

A ScriptRunner fragment is a single script body, so the modular source has to be joined into one file before it can be pasted into Jira. A profile in `profiles/` says which modules go in and which values to override:

```
# comment lines start with #
name = full
modules = internal-mail-domain people-reporter-assignee jsm portal share moved secured
text = config/text.en.groovy
MODE_ORDER = ['portal', 'share', 'moved', 'secured']
TEXT.genericEscalate = 'Still stuck? Ask your Jira administrators'
```

- `name` is the output file's name, `dist/<name>.groovy`; it defaults to the profile's file name.
- `modules` lists module names, resolved against `src/policy/` and `src/modes/`; each name must exist in exactly one of them. A file whose name starts with an underscore, such as `src/modes/_template.groovy`, is never built.
- `text` picks the text file, relative to `src/`; `config/text.en.groovy` is the default. A translation is one more text file and one line here.
- `NAME = value` replaces the value of a `final ... NAME = ...` line in `src/config/config.groovy`. The value replaces the rest of that line, so it must fit on one line, and the name must match exactly one line.
- `TEXT.key = value` replaces one entry of the text file. The value is a quoted Groovy string.

The assembled file is written in this order: a banner naming the profile and its modules; every `import` line of every included module, without duplicates; the `CONFIG` section (the values and the texts, with the overrides applied); the `DECIDE` section (`core/context`, the policy modules, the mode modules, `core/decide`); `core/entry`; and the `RENDER` section, with `src/render/client.js` inlined into the client block and its full-line comments removed.

Order matters inside `DECIDE`: a Groovy closure can only see script variables declared above it, so helpers come before the modes that call them. Every module declares what it defines and what it needs in its header, for example `// provides: isInternal` and `// requires: hasAppAccess escalationFor`, and the assembler refuses to build a profile whose requirements are not met. The order of the modules in a profile does not decide the order in which modes are tried; `MODE_ORDER` does.

After writing each file, the assembler runs `strip_client_comments.py --check` on it, so a comment or a stray dollar sign in the client script fails the build.

```
python build/assemble.py --profile full         build dist/full.groovy
python build/assemble.py --all                  build every profile
python build/assemble.py --all --check          exit 1 if any file in dist/ is stale
```

The section markers it writes (`// >>> CONFIG`, `// <<< CONFIG`, and the same for `DECIDE` and `RENDER`) are what `sync_tests.py` reads. [docs/EXTENDING.md](../docs/EXTENDING.md) explains how to write a module; [docs/RECIPES.md](../docs/RECIPES.md) shows profiles for common situations.

## strip_client_comments.py: why it exists

A ScriptRunner web panel that draws UI writes an HTML and JavaScript block into the page, here with `writer.write($/ ... /$)`. The server-side Groovy around it never leaves the server. The block inside it reaches every browser that renders the panel, and anyone can read it with "view page source". That includes comments.

Developer comments are where real data hides: the ticket that motivated a fix, the example name that exposed an edge case, the test issue used while debugging. In an earlier internal version of this fragment, two such comments, one with a real display name and one with an internal issue key, shipped to every browser that received a card. Several reviews had covered the card's text, the server logic and the client's behaviour; none read the shipped bytes. The lesson generalises beyond this project: when you harden what a feature says, also check what its own source carries.

The fix is structural, so it does not depend on anyone remembering:

- the client script's source, `src/render/client.js`, may explain itself only in full-line `//` comments, and `assemble.py` removes those when it inlines the file. Comments that share a line with code are not removed, so the source does not use them; what the script does is also explained in [docs/DESIGN.md](../docs/DESIGN.md);
- every assembled file is checked by this tool, and the build fails if a comment line survives;
- `--check` works on any fragment file, including your configured copy, so it can guard a CI pipeline or a pre-deploy step;
- the tool can also strip full-line comments itself, if you keep a commented working copy of a fragment, and it re-checks its own output.

It also guards against a Groovy trap. Inside a dollar-slashy string, `$name` interpolates a Groovy variable and `$/` is an escaped slash, so a stray dollar sign in client code (a jQuery call, a template literal) is rewritten by Groovy without any error. Every dollar sign inside a client block must be one of the allowed interpolations (`--allow`, default `dataJson,textJson`).

```
python build/strip_client_comments.py dist/full.groovy --check
python build/strip_client_comments.py my-working-copy.groovy -o my-stripped-copy.groovy
python build/strip_client_comments.py my-working-copy.groovy --in-place
```

Exit codes: 0 clean, 1 check failed (or a problem that makes stripping unsafe), 2 usage or structure error. In check mode the tool prints line numbers, never the comment text, so a CI log does not repeat what it found.

What it strips inside each client block: full-line `//` comments, and `/* ... */` or `<!-- ... -->` comments that start a line and end a line. A comment that shares a line with code is left in place and reported as a warning: this is a line-based tool, not a JavaScript parser. Use `--open` and `--close` to adapt it to other fragments whose client blocks are delimited differently.

## sync_tests.py

The tests in `tests/` must run the logic you deploy, not a copy that drifts. An assembled file marks three sections (`CONFIG`, `DECIDE`, `RENDER`); each test marks where a copy goes (`// >>> COPY <NAME>` ... `// <<< COPY <NAME>`). This script refreshes those regions from an assembled file, including its CONFIG values and its `import` lines, and flips the restricted-card kill switch in the copy used by the kill-switch test.

By default it reads `dist/full.groovy`. Point it at the file you actually deploy with `--source`.

```
python build/sync_tests.py                                   refresh from dist/full.groovy
python build/sync_tests.py --source my-configured.groovy     refresh from your configured copy
python build/sync_tests.py --check                           exit 1 if a test is stale
```

The source must contain exactly one `final boolean SECURED_CARD = true` line, because that is the line the kill-switch copy flips. If your deployed copy has the restricted card switched off, sync from a copy with it on.

## Suggested pre-deploy sequence

```
python build/assemble.py --profile <name>                    only if you changed src/ or a profile
python build/strip_client_comments.py <your file> --check
python build/sync_tests.py --source <your file>
```

Then run the three tests in the Script Console, deploy, and compare the saved fragment body with your file.

For a CI pipeline on the repository itself, the three checks that need no Jira are `python build/assemble.py --all --check`, `python build/strip_client_comments.py dist/<profile>.groovy --check` for each profile, and `python build/sync_tests.py --check`.
