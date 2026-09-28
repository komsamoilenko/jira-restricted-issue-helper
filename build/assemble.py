#!/usr/bin/env python3
"""Assemble one deployable fragment from the modules in src/ and a profile.

A ScriptRunner fragment is a single script body, so the modular source has to
be joined into one file before it can be pasted into Jira. A profile says which
modules go in and which CONFIG values to override:

    profiles/<name>.profile
        name = full
        modules = internal-mail-domain people-reporter-assignee jsm portal share moved secured
        text = config/text.en.groovy           (optional; this is the default)
        MODE_ORDER = ['portal', 'share', 'moved', 'secured']   (override a CONFIG value)
        TEXT.genericEscalate = 'Still stuck? Ask your Jira administrators'  (override a TEXT entry)

Module names are resolved against src/policy/ and src/modes/. The assembled
file is written to dist/<name>.groovy in this order:

    banner
    every import line of every included module (deduplicated)
    // >>> CONFIG   config/config.groovy + the text file, with overrides applied
    // <<< CONFIG
    // >>> DECIDE   core/context.groovy, the policy modules, the mode modules,
    // <<< DECIDE   core/decide.groovy
    core/entry.groovy
    // >>> RENDER   render/render.groovy with render/client.js inlined at the
    // <<< RENDER   @@CLIENT@@ line, full-line comments removed

Order matters inside DECIDE: a closure can only see script variables declared
above it, so helpers come before the modes that call them. Every module
declares what it defines and what it needs in its header:

    // provides: isInternal
    // requires: hasAppAccess escalationFor

and the assembler refuses to build a profile whose requirements are not met.
A mode declares `provides: mode:<name>`. Modules whose file name starts with
an underscore (the template) are never built.

After writing, the assembler runs build/strip_client_comments.py --check on
the result, so a comment or a stray dollar sign in the client script fails the
build.

Usage:
  python build/assemble.py --profile full         build dist/full.groovy
  python build/assemble.py --all                  build every profile
  python build/assemble.py --all --check          exit 1 if any dist file is stale

The section markers (>>> CONFIG and so on) are what build/sync_tests.py reads
to refresh the Script Console tests from dist/full.groovy.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src'
DIST = ROOT / 'dist'
PROFILES = ROOT / 'profiles'
STRIP = ROOT / 'build' / 'strip_client_comments.py'

VERSION = '1.1.0'
CLIENT_MARKER = '// @@CLIENT@@'

BANNER = """\
// ============================================================================
//  Restricted-issue helper for Jira's "You can't view this issue" page
//  Version {version} -- ScriptRunner for Jira Data Center fragment
//  Fragment type: "Show a web panel"   Location: atl.header.after.scripts
//  Weight: 100   Condition: none        Licence: MIT (see LICENSE)
// ----------------------------------------------------------------------------
//  ASSEMBLED FILE. Built by build/assemble.py from profile "{profile}" with
//  these modules: {modules}.
//  Do not edit it by hand: edit src/ and rebuild (docs/EXTENDING.md). Only the
//  CONFIG block below is meant to be edited in a deployed copy.
//
//  Renders NOTHING except on the two dead-end pages, where the logged-in user
//  cannot see the issue. It then takes over the error block and draws ONE
//  card. Every decision is made here, on the server; the browser receives a
//  small JSON payload and the script that draws it.
//
//  Covered pages:
//   /browse/<KEY>                     Jira core, block .issue-error
//   /projects/<P>/queues[/...]/<KEY>  Jira Service Management agent view,
//                                     stock "Snap! You can't view this page"
//                                     (<section id="unlicensed-project-type">)
//
//  Modes built into this file: {modes_built}.
//  Tried in MODE_ORDER; the generic card is the fallback. The full set:
//   portal     - a Service Management request the viewer CAN open on the portal
//   share      - a request the viewer cannot open, but whose reporter can add
//                them with the portal Share button
//   moved      - it USED to be a request but was moved out of the service desk
//   secured    - the issue's security level is what hides it: say so, point at
//                the level's "add one person to this issue" field, and name who
//                can fill it in (only when every gate in docs/DESIGN.md holds)
//   restricted - existence only: the issue exists and the viewer may not see
//                it, nothing else (off unless listed in MODE_ORDER)
//   missing    - no issue has this key (off unless listed; answers only at
//                RESTRICTED_SCOPE 'any-issue' with restricted also listed)
//   generic    - anything else -> Help Center + raise a request; identical for
//                a hidden issue and for a key that does not exist
//
//  Properties that hold for every mode: the payload defaults to the generic
//  card BEFORE any lookup, so an exception degrades to a usable card; the JSON
//  payload is HTML-escaped before it enters the inline <script>, and the
//  client inserts every server value as a text node; the client script
//  carries NO comments (build/strip_client_comments.py --check enforces it).
// ============================================================================
"""

HEADER_KEYS = ('provides', 'requires')


class BuildError(SystemExit):
    def __init__(self, msg: str):
        super().__init__(f'error: {msg}')


def read(path: Path) -> str:
    try:
        return path.read_text(encoding='utf-8')
    except FileNotFoundError:
        raise BuildError(f'missing file {path.relative_to(ROOT)}')


def parse_profile(path: Path) -> dict:
    prof = {'name': path.stem, 'modules': [], 'text': 'config/text.en.groovy', 'set': [], 'text_set': []}
    for n, raw in enumerate(read(path).splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise BuildError(f'{path.name}:{n}: expected "key = value"')
        key, value = (s.strip() for s in line.split('=', 1))
        if key == 'name':
            prof['name'] = value
        elif key == 'modules':
            prof['modules'] = value.split()
        elif key == 'text':
            prof['text'] = value
        elif key.startswith('TEXT.'):
            prof['text_set'].append((key[len('TEXT.'):], value, n))
        elif re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            prof['set'].append((key, value, n))
        else:
            raise BuildError(f'{path.name}:{n}: unknown key {key!r}')
    return prof


def resolve_module(name: str) -> tuple[str, Path]:
    if name.startswith('_'):
        raise BuildError(f'module {name!r} is a template and cannot be built')
    hits = [(kind, SRC / kind / f'{name}.groovy') for kind in ('policy', 'modes')
            if (SRC / kind / f'{name}.groovy').is_file()]
    if len(hits) != 1:
        raise BuildError(f'module {name!r}: expected exactly one of src/policy/{name}.groovy, '
                         f'src/modes/{name}.groovy (found {len(hits)})')
    return hits[0]


def split_module(text: str) -> tuple[list[str], set[str], set[str], str]:
    """Return (import lines, provides, requires, body without imports/header keys)."""
    imports: list[str] = []
    provides: set[str] = set()
    requires: set[str] = set()
    body: list[str] = []
    for line in text.splitlines():
        s = line.strip()
        if s.startswith('import '):
            imports.append(s)
            continue
        m = re.match(r'//\s*(provides|requires):\s*(.*)$', s)
        if m:
            (provides if m.group(1) == 'provides' else requires).update(m.group(2).split())
            continue
        body.append(line)
    return imports, provides, requires, '\n'.join(body).strip('\n') + '\n'


def balanced(s: str) -> bool:
    return all(s.count(o) == s.count(c) for o, c in (('[', ']'), ('(', ')'), ('{', '}')))


def apply_overrides(config: str, text: str, prof: dict, pname: str) -> tuple[str, str]:
    # Both replacements work on ONE physical line: the value of a CONFIG
    # variable (`final <type> NAME = ...`) or one entry of the TEXT map
    # (`key : ...,`). A value that spans several lines in config.groovy cannot
    # be overridden from a profile; the build refuses rather than corrupting it.
    for key, value, n in prof['set']:
        pat = re.compile(r'^(final\s+\w+\s+' + re.escape(key) + r'\s*=\s*)(.*)$', re.M)
        hits = pat.findall(config)
        if len(hits) != 1:
            raise BuildError(f'{pname}:{n}: CONFIG value {key} matched {len(hits)} line(s), expected 1')
        if not balanced(hits[0][1]) or not balanced(value):
            raise BuildError(f'{pname}:{n}: CONFIG value {key} spans several lines or the '
                             f'override is unbalanced; only single-line values can be overridden')
        config = pat.sub(lambda m: m.group(1) + value, config)
    for key, value, n in prof['text_set']:
        value = value.rstrip().rstrip(',').rstrip()
        pat = re.compile(r'^([ \t]*' + re.escape(key) + r'[ \t]*:[ \t]*).*?,?[ \t]*$', re.M)
        text, count = pat.subn(lambda m: m.group(1) + value + ',', text)
        if count != 1:
            raise BuildError(f'{pname}:{n}: TEXT entry {key} matched {count} line(s), expected 1')
    return config, text


def client_script() -> str:
    lines = [l for l in read(SRC / 'render' / 'client.js').splitlines()
             if not l.lstrip().startswith('//')]
    while lines and not lines[0].strip():
        lines.pop(0)
    return '\n'.join(lines)


def assemble(prof: dict, pname: str) -> str:
    modules = [(name,) + resolve_module(name) for name in prof['modules']]
    policies = [(n, p) for n, k, p in modules if k == 'policy']
    modes = [(n, p) for n, k, p in modules if k == 'modes']

    pieces: list[tuple[str, Path]] = [('core/context', SRC / 'core' / 'context.groovy')]
    pieces += [(f'policy/{n}', p) for n, p in policies]
    pieces += [(f'modes/{n}', p) for n, p in modes]
    pieces += [('core/decide', SRC / 'core' / 'decide.groovy'),
               ('core/entry', SRC / 'core' / 'entry.groovy'),
               ('render/render', SRC / 'render' / 'render.groovy')]

    seen = [n for n, _, _ in modules]
    dupes = sorted({n for n in seen if seen.count(n) > 1})
    if dupes:
        raise BuildError(f'{pname}: module(s) listed more than once: {", ".join(dupes)}')

    # Walk the pieces in output order: a name must be provided ABOVE the module
    # that requires it (a closure in a Groovy script only sees the script
    # variables declared before it), and no name may be provided twice (Groovy
    # refuses a second `def` of the same name in one scope).
    imports: list[str] = []
    provided_by: dict[str, str] = {}
    bodies: dict[str, str] = {}
    for label, path in pieces:
        imp, prov, req, body = split_module(read(path))
        for i in imp:
            if i not in imports:
                imports.append(i)
        missing = sorted(req - set(provided_by))
        if missing:
            raise BuildError(f'{pname}: {label} requires {", ".join(missing)}, which no module '
                             f'placed before it provides (check the profile order)')
        for name in sorted(prov):
            if name in provided_by:
                raise BuildError(f'{pname}: {name} is provided by both {provided_by[name]} '
                                 f'and {label}; build exactly one of them')
            provided_by[name] = label
        bodies[label] = body

    config = read(SRC / 'config' / 'config.groovy')
    text = read(SRC / prof['text'])
    config, text = apply_overrides(config, text, prof, pname)

    render = bodies['render/render']
    if render.count(CLIENT_MARKER) != 1:
        raise BuildError('render/render.groovy must contain the @@CLIENT@@ marker exactly once')
    render = render.replace(CLIENT_MARKER, client_script())

    out: list[str] = [BANNER.format(version=VERSION, profile=prof['name'],
                                    modules=', '.join(prof['modules']),
                                    modes_built=', '.join(n for n, _ in modes) or 'none (generic only)')]
    out.append('\n'.join(imports) + '\n\n')
    out.append('// >>> CONFIG =================================================================\n')
    out.append(config.strip('\n') + '\n\n')
    out.append(text.strip('\n') + '\n')
    out.append('// <<< CONFIG =================================================================\n\n')
    out.append('// >>> DECIDE -- build/sync_tests.py copies everything from here down to\n'
               '//               "<<< DECIDE" verbatim into tests/decision_test*.groovy, so\n'
               '//               the decision tests run the deployed logic, not a copy of it.\n\n')
    for label, _ in pieces:
        if label in ('core/entry', 'render/render'):
            continue
        out.append(bodies[label] + '\n')
    out.append('// <<< DECIDE\n\n')
    out.append(bodies['core/entry'] + '\n')
    out.append('// >>> RENDER -- build/sync_tests.py copies this block verbatim into\n'
               '//               tests/render_test.groovy. The client script inside must\n'
               '//               stay free of comments.\n')
    out.append(render)
    out.append('// <<< RENDER\n')
    return ''.join(out)


def check_client(content: str, label: str) -> None:
    """Run build/strip_client_comments.py --check on the assembled text, before
    it replaces anything in dist/."""
    tmp = DIST / f'.{label}.checking.groovy'
    DIST.mkdir(exist_ok=True)
    tmp.write_text(content, encoding='utf-8', newline='\n')
    try:
        r = subprocess.run([sys.executable, str(STRIP), str(tmp), '--check'],
                           capture_output=True, text=True)
    finally:
        tmp.unlink(missing_ok=True)
    if r.returncode != 0:
        raise BuildError(f'client check failed for profile {label}:\n' + (r.stdout + r.stderr).strip())


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    which = ap.add_mutually_exclusive_group(required=True)
    which.add_argument('--profile', help='profile name (profiles/<name>.profile)')
    which.add_argument('--all', action='store_true', help='build every profile')
    ap.add_argument('--check', action='store_true', help='report only; exit 1 if a dist file is stale')
    args = ap.parse_args(argv)

    paths = sorted(PROFILES.glob('*.profile')) if args.all else [PROFILES / f'{args.profile}.profile']
    if not paths:
        raise BuildError('no profiles found in profiles/')
    stale = 0
    for ppath in paths:
        prof = parse_profile(ppath)
        content = assemble(prof, ppath.name)
        check_client(content, prof['name'])
        target = DIST / f'{prof["name"]}.groovy'
        old = target.read_text(encoding='utf-8') if target.is_file() else None
        if args.check:
            state = 'ok' if old == content else 'STALE'
            stale += state == 'STALE'
            print(f'{state:6} {target.relative_to(ROOT)}')
            continue
        target.write_text(content, encoding='utf-8', newline='\n')
        print(f'built  {target.relative_to(ROOT)}  ({content.count(chr(10))} lines, '
              f'{"unchanged" if old == content else "updated"})')
    if args.check:
        print(f'{len(paths)} profile(s), {stale} stale: {"OK" if not stale else "run build/assemble.py --all"}')
        return 1 if stale else 0
    return 0


if __name__ == '__main__':
    sys.exit(main())
