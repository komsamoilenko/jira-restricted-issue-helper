# Changelog

All notable changes to this project are recorded here. Versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-26

First public release. Functionally identical to internal version 7.4 (2026-09-24).

### Changed for the public release

With equivalent configuration, none of these changes alters what any viewer sees.

- Every deployment-specific value (links, the request-type field id, scheme and level ids, automation account names) moved into one `CONFIG` block at the top of the fragment, with placeholder defaults that keep the restricted card switched off until they are replaced.
- All user-facing text moved into `CONFIG` as the `TEXT` map, with `{placeholders}` for the dynamic parts. The rendered cards and copy-ready messages are character-for-character the same as 7.4's with the default text.
- The internal-viewer check became a configurable policy: `INTERNAL_MAIL_DOMAINS` (a list of domain patterns) and `INTERNAL_REQUIRE_USERNAME` (default `true`); see [docs/CONFIG.md](docs/CONFIG.md).
- The name tokenizer's Latin letter range is written as JavaScript escapes, so the source file is plain ASCII. The regular expression the browser evaluates is unchanged.
- Tests use synthetic data and are refreshed from the fragment by `build/sync_tests.py`; the comment-stripping build step is a standalone tool, `build/strip_client_comments.py`.

## Internal history

Summarised; the internal versions were not published.

### 7.4 - 2026-09-24

- The build strips every full-line comment from the client script and fails if one survives.
- The lesson: everything inside the client script reaches every browser that gets a card, comments included. Two developer comments, one carrying a real display name (present since version 4) and one an internal issue key (written in 7.0, first shipped in 7.2), had been shipping in the page source. Reviews had read the card's text, the server logic and the client's behaviour, but not the shipped bytes. The fix is structural rather than a rule to remember: explanations live in server-side comments and documentation, and the build removes anything else.

### 7.3 - 2026-09-24

- Removed a maintainer-only preview mode. The card now renders only from the server-side decision.

### 7.2 - 2026-09-23

- Hardening after an independent re-verification of 7.1: the internal-viewer check tightened to require two matching signals instead of one (in 1.0.0, `INTERNAL_REQUIRE_USERNAME`).
- More compartments whose names are the secret added to the exclusion list.
- Scheme ids compared as `Long`, so a list of integers can no longer switch the card off silently.

### 7.1 - 2026-09-23 (not deployed)

- Two independent adversarial reviews of 7.0 found a blocker before release: application access is not proof of being staff, and on some compartments the level's name and the people on the issue are exactly what the level protects. The restricted card would have shown both to accounts that should never learn such issues exist.
- Added the gates that define the design: the internal-viewer policy; schemes in scope with an exclusion list; project not archived and issue editable; "would the permission scheme let the viewer in with issue security left out, or through the suggested field".
- Accounts without an e-mail address fall back to their username in copy-ready messages.
- Copy buttons announce their result to screen readers; the people chips wrap long names.

### 7.0 - 2026-09-23 (not deployed)

- New `secured` card for issues hidden by their security level: says so, points at the level's multi-user picker field that opens this one issue, names up to two people who can fill it in, and offers a copy-ready message. Kill switch `SECURED_CARD`.
- The name tokenizer covers Latin Extended letters, so names with diacritics are greeted correctly; the greeting uses a first name only when the first word needed no cleaning.

### 6 - 2026-09-18

- Covers the Service Management agent view (queues), whose dead end is gated on the agent licence rather than on Browse Projects.
- New `moved` card for issues moved out of a service desk, detected by key history with the request-type field as a fallback (built as version 5, first deployed with 6). Kill switch `MOVED_CARD`.

### 4 - 2026-08-21

Hardening after an adversarial audit:

- The share card requires the viewer to hold application access, so portal-only customers never learn that a request exists.
- The reporter's e-mail address removed from the payload; the display name stays.
- The JSON payload is HTML-escaped before it enters the inline script, so a value containing `</script>` cannot break out.
- The request branch is gated on the project type, not on the request-type field alone, which issues keep after moving out of a service desk.
- The payload defaults to the generic card before any lookup, so a failure degrades to a usable card and "hidden" looks the same as "absent".
- The URL match is case-insensitive and the key is taken from the resolved issue, not from the URL.

### 1 to 3 - 2026-08-21

- The generic card replacing the dead end, the `portal` card for requests the viewer can open on the customer portal, and the `share` card.
