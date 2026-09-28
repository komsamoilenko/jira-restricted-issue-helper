# Contributing

Scenarios, bug reports, modes, policies and translations are all welcome. Please read [docs/DESIGN.md](docs/DESIGN.md) first: every change to what a card says is a change to what it discloses.

## Build and check

You need Python 3.10 or later and nothing else. From the repository root:

```
python build/assemble.py --all
python build/strip_client_comments.py dist/<profile>.groovy --check
python build/sync_tests.py
```

The first line rebuilds every profile into `dist/`, the second checks one assembled file for comments and stray dollar signs in the client script, and the third refreshes the tests from `dist/full.groovy`. Before you push, run the three checks that CI runs:

```
python build/assemble.py --all --check
for f in dist/*.groovy; do python build/strip_client_comments.py "$f" --check; done
python build/sync_tests.py --check
```

Commit `dist/` and the tests as they come out of the build. The tests in the repository are synced from `dist/full.groovy`; if you synced them from another file with `--source`, sync them back before you commit.

## Run the tests on an instance

The three tests in [tests/](tests/) run in the ScriptRunner Script Console and only read. [tests/README.md](tests/README.md) has the details.

1. `tests/render_test.groovy` needs no users or issues. Run it first: it renders every card variant, and a compilation error shows up here.
2. `tests/decision_test.groovy` runs `decide()`, exactly as deployed, for viewer and issue pairs. Replace the synthetic accounts and keys in `CASES` with ones on your instance that match each scenario.
3. `tests/decision_test_secured_off.groovy` proves that `SECURED_CARD = false` restores the generic card.

Use a test instance where you have one.

## A good pull request

- **Starts from a scenario**: who is blocked, on which page, why, and what the card should say to whom. The scenario issue template asks exactly this.
- **Adds or changes a test case** in `tests/decision_test.groovy`, including one where a viewer who fails the new gates gets the generic card. A change to the client also needs a case in `tests/render_test.groovy`.
- **Updates the documentation it touches**: the card table in [docs/DESIGN.md](docs/DESIGN.md), [docs/CONFIG.md](docs/CONFIG.md) for a new value, [docs/RECIPES.md](docs/RECIPES.md) for a new situation, and [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), with an evidence label, for a new API call.
- **Passes the three checks** above, with `dist/` rebuilt.
- **Holds no real data**: no real names, issue keys, hostnames, e-mail addresses, or field, scheme or level ids from any instance. Use `example.com`, keys such as `DEMO-101`, and placeholder ids. Screenshots use invented data.

One change per pull request. [docs/EXTENDING.md](docs/EXTENDING.md) explains the build, the mode and policy contracts, and how to add a mode step by step.

## Security

If you think a card discloses something it should not, do not open a public issue. Report it privately through GitHub's private vulnerability reporting for this repository (**Security > Report a vulnerability**). [SECURITY.md](SECURITY.md) says what a useful report contains.

## No comments in the client script

Everything in the client script reaches every browser that gets a card, comments included. `src/render/client.js` may carry full-line `//` comments, which the build removes; it must not carry a comment on a line with code, or any dollar sign other than `$dataJson` and `$textJson`. Every server value goes into the page as a text node. The rules are in [docs/EXTENDING.md](docs/EXTENDING.md#rules-for-the-client-script).

## Licence

MIT. By contributing, you agree that your contribution is released under the same licence. See [LICENSE](LICENSE).
