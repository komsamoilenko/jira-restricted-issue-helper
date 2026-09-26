#!/usr/bin/env python3
"""Copy the deployed sections of the fragment into the Script Console tests.

The tests in tests/ must run the logic you deploy, not a hand-made copy of it
that drifts. The fragment marks its sections with comment lines:

    // >>> CONFIG ... // <<< CONFIG     every deployment-specific value
    // >>> DECIDE ... // <<< DECIDE     the server-side decision
    // >>> RENDER ... // <<< RENDER     payload -> inline client script

and every test marks where a copy goes:

    // >>> COPY <NAME>
    // <<< COPY <NAME>

NAME is IMPORTS (every ``import`` line of the fragment), CONFIG, DECIDE,
RENDER, or CONFIG_SECURED_OFF (CONFIG with the restricted-card kill switch
turned off, for the kill-switch test).

Usage:
  python build/sync_tests.py            refresh every test from src/
  python build/sync_tests.py --check    exit 1 if any test is out of date
  python build/sync_tests.py --source path/to/your-configured-fragment.groovy

Run it after every change to the fragment, including CONFIG edits, so the
tests use your real values.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / 'src' / 'browse-error-helper.groovy'
TESTS = sorted((ROOT / 'tests').glob('*.groovy'))

KILL_SWITCH_ON = 'final boolean SECURED_CARD = true'
KILL_SWITCH_OFF = 'final boolean SECURED_CARD = false'


def section(lines: list[str], name: str) -> str:
    """Lines from '// >>> NAME' through '// <<< NAME', inclusive."""
    starts = [i for i, l in enumerate(lines) if l.split() and l.startswith('// >>> ' + name)
              and l[len('// >>> ' + name):][:1] in ('', ' ', '\n', '\r')]
    ends = [i for i, l in enumerate(lines) if l.startswith('// <<< ' + name)
            and l[len('// <<< ' + name):][:1] in ('', ' ', '\n', '\r')]
    if len(starts) != 1 or len(ends) != 1 or ends[0] < starts[0]:
        raise SystemExit(f'error: section {name} must be marked exactly once in the source '
                         f'(found {len(starts)} start, {len(ends)} end marker(s))')
    return ''.join(lines[starts[0]:ends[0] + 1])


def sections(src: Path) -> dict[str, str]:
    text = src.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    out = {
        'IMPORTS': ''.join(l for l in lines if l.startswith('import ')),
        'CONFIG': section(lines, 'CONFIG'),
        'DECIDE': section(lines, 'DECIDE'),
        'RENDER': section(lines, 'RENDER'),
    }
    if out['CONFIG'].count(KILL_SWITCH_ON) != 1:
        raise SystemExit(f'error: expected exactly one {KILL_SWITCH_ON!r} in CONFIG')
    out['CONFIG_SECURED_OFF'] = out['CONFIG'].replace(KILL_SWITCH_ON, KILL_SWITCH_OFF)
    return out


def fill(test_text: str, secs: dict[str, str], where: Path) -> str:
    lines = test_text.splitlines(keepends=True)
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        if line.startswith('// >>> COPY '):
            name = line[len('// >>> COPY '):].strip()
            if name not in secs:
                raise SystemExit(f'error: {where.name}:{i + 1}: unknown section {name!r}')
            j = i + 1
            while j < len(lines) and not lines[j].startswith('// <<< COPY ' + name):
                j += 1
            if j == len(lines):
                raise SystemExit(f'error: {where.name}:{i + 1}: no closing "// <<< COPY {name}"')
            body = secs[name]
            if body and not body.endswith('\n'):
                body += '\n'
            out.append(body)
            out.append(lines[j])
            i = j + 1
            continue
        i += 1
    return ''.join(out)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('--source', type=Path, default=DEFAULT_SOURCE)
    ap.add_argument('--check', action='store_true', help='report only; exit 1 if a test is stale')
    args = ap.parse_args(argv)

    secs = sections(args.source)
    stale = []
    for test in TESTS:
        old = test.read_text(encoding='utf-8')
        new = fill(old, secs, test)
        if new != old:
            stale.append(test)
            if not args.check:
                test.write_text(new, encoding='utf-8', newline='')
    for test in stale:
        print(('STALE ' if args.check else 'updated ') + str(test.relative_to(ROOT)))
    if args.check:
        print(f'{len(TESTS)} test(s), {len(stale)} stale: {"OK" if not stale else "run build/sync_tests.py"}')
        return 1 if stale else 0
    print(f'{len(TESTS)} test(s), {len(stale)} updated from {args.source.name}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
