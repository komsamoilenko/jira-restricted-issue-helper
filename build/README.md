# Build tools

Two small Python scripts (3.10 or later, standard library only). Neither talks to Jira.

## strip_client_comments.py: why it exists

A ScriptRunner web panel that draws UI writes an HTML and JavaScript block into the page, here with `writer.write($/ ... /$)`. The server-side Groovy around it never leaves the server. The block inside it reaches every browser that renders the panel, and anyone can read it with "view page source". That includes comments.

Developer comments are where real data hides: the ticket that motivated a fix, the example name that exposed an edge case, the test issue used while debugging. In an earlier internal version of this fragment, two such comments, one with a real display name and one with an internal issue key, shipped to every browser that received a card. Several reviews had covered the card's text, the server logic and the client's behaviour; none read the shipped bytes. The lesson generalises beyond this project: when you harden what a feature says, also check what its own source carries.

The fix is structural, so it does not depend on anyone remembering:

- the client script in `src/` carries no comments at all; what it does is explained in server-side comments just above it and in [docs/DESIGN.md](../docs/DESIGN.md);
- this tool removes full-line comments from client blocks when you build a deployable copy from a commented working file, and re-checks its own output;
- `--check` fails if a comment line is present, so it can guard a CI pipeline or a pre-deploy step.

It also guards against a Groovy trap. Inside a dollar-slashy string, `$name` interpolates a Groovy variable and `$/` is an escaped slash, so a stray dollar sign in client code (a jQuery call, a template literal) is rewritten by Groovy without any error. Every dollar sign inside a client block must be one of the allowed interpolations (`--allow`, default `dataJson,textJson`).

```
python build/strip_client_comments.py src/browse-error-helper.groovy --check
python build/strip_client_comments.py my-working-copy.groovy -o dist/browse-error-helper.groovy
python build/strip_client_comments.py my-working-copy.groovy --in-place
```

Exit codes: 0 clean, 1 check failed (or a problem that makes stripping unsafe), 2 usage or structure error. In check mode the tool prints line numbers, never the comment text, so a CI log does not repeat what it found.

What it strips inside each client block: full-line `//` comments, and `/* ... */` or `<!-- ... -->` comments that start a line and end a line. A comment that shares a line with code is left in place and reported as a warning: this is a line-based tool, not a JavaScript parser. Use `--open` and `--close` to adapt it to other fragments whose client blocks are delimited differently.

## sync_tests.py

The tests in `tests/` must run the logic you deploy, not a copy that drifts. The fragment marks three sections (`CONFIG`, `DECIDE`, `RENDER`); each test marks where a copy goes (`// >>> COPY <NAME>` ... `// <<< COPY <NAME>`). This script refreshes those regions from the fragment, including your CONFIG values, and flips the restricted-card kill switch in the copy used by the kill-switch test.

```
python build/sync_tests.py                                 refresh from src/
python build/sync_tests.py --source my-configured.groovy   refresh from your configured copy
python build/sync_tests.py --check                         exit 1 if a test is stale
```

## Suggested pre-deploy sequence

```
python build/strip_client_comments.py <your fragment> --check
python build/sync_tests.py --source <your fragment>
```

Then run the three tests in the Script Console, deploy, and compare the saved fragment body with your file.
