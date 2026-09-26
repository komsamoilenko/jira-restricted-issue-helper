#!/usr/bin/env python3
"""Strip comments from the client-side script blocks of a ScriptRunner fragment.

A ScriptRunner web panel that draws UI writes an HTML/JavaScript block into the
page, typically with ``writer.write($/ ... /$)``. Everything inside that block
reaches every browser that renders the panel: view the page source and it is
there, comments included. Server-side Groovy comments never leave the server.

This tool removes full-line comments from the client blocks only, and guards
against the Groovy dollar-slashy traps that silently rewrite client code.

Usage:
  python build/strip_client_comments.py SOURCE --check
      Exit 1 if any client block still holds a comment line or an unexpected
      dollar sign. Use it in CI and before every deploy.
  python build/strip_client_comments.py SOURCE -o OUTPUT
  python build/strip_client_comments.py SOURCE --in-place
      Write the stripped fragment, then re-check the result.

What counts as a client block: from a code line (not a comment) that contains
the opening token (default ``writer.write($/``) to the next line containing the
closing token (default ``/$)``). Both tokens can be changed with --open and
--close, and a file may hold several blocks.

What is stripped inside a block:
  - full-line ``//`` comments;
  - ``/* ... */`` and ``<!-- ... -->`` comments that start a line and end a
    line (one line or several).
A comment that shares a line with code is NOT stripped: this is a line-based
tool, not a JavaScript parser. ``--check`` reports any remaining ``/*`` or
``<!--`` as a warning so a person can look at it.

The dollar guard: inside a dollar-slashy string, ``$name`` interpolates a
Groovy variable and ``$/`` is an escaped slash, so a stray dollar sign in
client code (a jQuery call, a template literal, a regex anchor before a slash)
is rewritten by Groovy without any error. Every dollar sign inside a block must
be an interpolation of a name listed with --allow (default: dataJson,textJson).

Exit codes: 0 clean, 1 check failed, 2 usage or structure error.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

DEFAULT_OPEN = 'writer.write($/'
DEFAULT_CLOSE = '/$)'
DEFAULT_ALLOW = 'dataJson,textJson'

BLOCK_PAIRS = (('/*', '*/'), ('<!--', '-->'))


def is_comment_line(line: str) -> bool:
    s = line.lstrip()
    return s.startswith('//') or s.startswith('/*') or s.startswith('*')


def find_blocks(lines: list[str], open_tok: str, close_tok: str) -> list[tuple[int, int]]:
    """Return (open_line, close_line) index pairs; content is strictly between them."""
    blocks = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if open_tok in line and not is_comment_line(line):
            j = i + 1
            while j < len(lines) and close_tok not in lines[j]:
                j += 1
            if j == len(lines):
                raise SystemExit(f'error: client block opened on line {i + 1} is never closed '
                                 f'with {close_tok!r}')
            blocks.append((i, j))
            i = j + 1
        else:
            i += 1
    return blocks


def comment_lines(lines: list[str], start: int, end: int) -> tuple[set[int], list[str]]:
    """Line indexes (start < idx < end) that are removable comments, plus problems."""
    drop: set[int] = set()
    problems: list[str] = []
    k = start + 1
    while k < end:
        body = lines[k].rstrip('\r\n')
        s = body.lstrip()
        if s.startswith('//'):
            drop.add(k)
            k += 1
            continue
        pair = next(((o, c) for o, c in BLOCK_PAIRS if s.startswith(o)), None)
        if pair is None:
            k += 1
            continue
        opener, closer = pair
        m = k
        while m < end:
            tail = lines[m].rstrip('\r\n')
            search_from = len(tail) - len(tail.lstrip()) + len(opener) if m == k else 0
            pos = tail.find(closer, search_from)
            if pos != -1:
                if tail[pos + len(closer):].strip():
                    problems.append(f'line {m + 1}: comment ends on a line that also holds code; '
                                    f'not stripped, remove it by hand')
                    m = -1
                break
            m += 1
        if m == -1:
            k += 1
            continue
        if m >= end:
            problems.append(f'line {k + 1}: comment opened with {opener!r} is not closed inside '
                            f'the client block')
            k += 1
            continue
        drop.update(range(k, m + 1))
        k = m + 1
    return drop, problems


def dollar_problems(lines: list[str], start: int, end: int, allow: set[str]) -> list[str]:
    out = []
    for k in range(start + 1, end):
        for m in re.finditer(r'\$', lines[k]):
            name = re.match(r'[A-Za-z_][A-Za-z0-9_]*', lines[k][m.end():])
            if name is None or name.group(0) not in allow:
                out.append(f'line {k + 1}, column {m.start() + 1}: dollar sign that Groovy would '
                           f'rewrite (allowed interpolations: {", ".join(sorted(allow)) or "none"})')
    return out


def warnings_for(lines: list[str], start: int, end: int, drop: set[int]) -> list[str]:
    out = []
    for k in range(start + 1, end):
        if k in drop:
            continue
        for tok in ('/*', '<!--'):
            if tok in lines[k]:
                out.append(f'line {k + 1}: {tok!r} left in place (shares a line with code, or is '
                           f'inside a string); check it by hand')
    return out


def analyse(lines: list[str], open_tok: str, close_tok: str, allow: set[str]):
    blocks = find_blocks(lines, open_tok, close_tok)
    drop: set[int] = set()
    problems: list[str] = []
    for a, b in blocks:
        d, p = comment_lines(lines, a, b)
        drop |= d
        problems += p
        problems += dollar_problems(lines, a, b, allow)
    return blocks, drop, problems


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('source', type=Path)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true', help='report only; exit 1 if not clean')
    mode.add_argument('-o', '--output', type=Path, help='write the stripped fragment here')
    mode.add_argument('--in-place', action='store_true', help='overwrite SOURCE')
    ap.add_argument('--open', default=DEFAULT_OPEN, help=f'opening token (default {DEFAULT_OPEN!r})')
    ap.add_argument('--close', default=DEFAULT_CLOSE, help=f'closing token (default {DEFAULT_CLOSE!r})')
    ap.add_argument('--allow', default=DEFAULT_ALLOW,
                    help=f'comma-separated interpolations allowed in a block (default {DEFAULT_ALLOW!r})')
    args = ap.parse_args(argv)

    if not args.source.is_file():
        print(f'error: {args.source} is not a file', file=sys.stderr)
        return 2
    allow = {a.strip() for a in args.allow.split(',') if a.strip()}
    text = args.source.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)

    blocks, drop, problems = analyse(lines, args.open, args.close, allow)
    if not blocks:
        print(f'error: no client block found (opening token {args.open!r})', file=sys.stderr)
        return 2
    warnings = [w for a, b in blocks for w in warnings_for(lines, a, b, drop)]

    if args.check:
        for k in sorted(drop):
            print(f'{args.source}:{k + 1}: comment line inside a client block')
        for p in problems:
            print(f'{args.source}: {p}')
        for w in warnings:
            print(f'{args.source}: warning: {w}')
        clean = not drop and not problems
        print(f'{len(blocks)} client block(s), {len(drop)} comment line(s), '
              f'{len(problems)} problem(s): {"CLEAN" if clean else "NOT CLEAN"}')
        return 0 if clean else 1

    if problems:
        for p in problems:
            print(f'{args.source}: {p}', file=sys.stderr)
        print('refusing to write: fix the problems above first', file=sys.stderr)
        return 1

    stripped = ''.join(line for k, line in enumerate(lines) if k not in drop)
    again = stripped.splitlines(keepends=True)
    _, drop2, problems2 = analyse(again, args.open, args.close, allow)
    if drop2 or problems2:
        print('internal error: comments survived stripping; nothing written', file=sys.stderr)
        return 1

    target = args.source if args.in_place else args.output
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(stripped, encoding='utf-8', newline='')
    for w in warnings:
        print(f'warning: {w}', file=sys.stderr)
    print(f'{len(blocks)} client block(s), {len(drop)} comment line(s) removed -> {target}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
