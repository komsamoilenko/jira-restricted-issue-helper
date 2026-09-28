# Extending the helper

There are three ways to make the helper fit your instance, from least to most code:

1. **A profile.** Pick modules and override CONFIG or TEXT values. No Groovy. [RECIPES.md](RECIPES.md) covers the common cases.
2. **A policy module.** Replace a decision the modes rely on: who counts as internal, or whom the restricted card may name.
3. **A mode module.** Add a card for a situation the shipped modes do not cover.

Whatever you change, you still deploy one file.

## How the build works

A ScriptRunner fragment is a single script body. The source in `src/` is split into modules so that a mode or a policy can be read, replaced and tested on its own, and [build/assemble.py](../build/assemble.py) joins the modules a profile names into one file, `dist/<name>.groovy`. The script's docstring lists the exact layout of the output.

### Profiles

A profile is a text file in `profiles/`:

```
# Finance: the restricted card only, "internal" means the finance staff
# group, and one line of text changed.
name = finance
modules = internal-group people-reporter-assignee secured
MODE_ORDER = ['secured']
INTERNAL_GROUPS = ['finance-staff']
TEXT.securedTitle = 'This finance issue is restricted'
```

- Blank lines and lines starting with `#` are ignored.
- `name` is the output file, `dist/<name>.groovy`. It defaults to the profile's file name.
- `modules` lists module names, separated by spaces. Each must resolve to exactly one of `src/policy/<name>.groovy` or `src/modes/<name>.groovy`. The core and render modules are always built in and are not listed. A name listed twice, or starting with `_` (the template), is refused.
- `text` is the text file, relative to `src/`. The default is `config/text.en.groovy`.
- `NAME = value`, with an upper-case name, replaces the right-hand side of the one line `final <Type> NAME = ...` in `config/config.groovy`. The value is Groovy source and is copied as written: quote strings, keep the `L` on ids. Only single-line values can be overridden; the build refuses a value that spans lines.
- `TEXT.key = 'value'` replaces one entry of the `TEXT` map in the text file. It must match exactly one entry. Quote the value; the trailing comma is added for you.

### Order, and why it is fixed

The assembled file always has the same order:

1. the banner;
2. every `import` line of every included module, deduplicated;
3. CONFIG: `config/config.groovy`, then the text file, with the profile's overrides applied;
4. DECIDE: `core/context`, the policy modules in the order `modules` lists them, the mode modules in the same order, then `core/decide`;
5. `core/entry`;
6. RENDER: `render/render.groovy`, with `render/client.js` inlined at its `@@CLIENT@@` line and the client's full-line comments removed.

The order matters because of how a Groovy script sees its own variables. `def name = ...` at the top level of a script declares a local variable of the script body, and a closure can use only the local variables declared above it. A closure that uses a name declared further down still compiles: Groovy treats the name as a property of the script, looks it up when the closure runs, and throws `MissingPropertyException` (or `MissingMethodException` for a call). Inside a mode, `decide()` treats that exception as "no answer". A mode placed above a helper it needs would therefore never answer, and nothing would say so: every viewer would quietly get the generic card.

So CONFIG comes first, because every closure reads it. Helpers come next, then the policies that use them, then the modes that use both, then `decide()`, which walks the modes, then the entry, which calls `decide()`, then the renderer, which reads the entry's `payload` and `pageKind`.

For the same reason, helpers are closures (`def helper = { ... }`), not script methods (`def helper() { ... }`). Groovy compiles a script method into the script class, where it cannot see the local variables of the script body, CONFIG included.

### provides and requires

Every module declares what it defines and what it needs, in comment lines:

```groovy
// provides: isInternal
// requires: hasAppAccess escalationFor
```

The assembler reads these lines, removes them from the output, and walks the modules in output order. It refuses a build when a module requires a name that no module placed above it provides (within the policies and within the modes, the order in `modules` is the output order), and when two modules provide the same name: Groovy would refuse a second `def` of one name in one scope. A mode provides `mode:<name>`. CONFIG values are not declared: `config/config.groovy` is always included, above everything, so every module can read every value.

Import lines are lifted out of every module and placed, deduplicated, at the top of the file. An imported class is then visible to every module, but keep each import in the module that uses it, so that the module still states its own dependencies.

Before it replaces a file in `dist/`, the assembler runs `build/strip_client_comments.py --check` on the assembled text. With `--check`, it builds every profile, runs the same client check, and reports which files in `dist/` are stale, without replacing them.

## The mode contract

A mode is one closure in the `MODES` registry:

```groovy
// modes/<name>.groovy -- one line on what the card is for.
// provides: mode:<name>
// requires: hasAppAccess escalationFor
MODES['<name>'] = { ctx ->
    if (<not my case>) { return null }          // null: try the next mode
    if (<the viewer fails a gate>) { return null }
    return [mode: '<name>', ...]                  // all of this reaches the browser
}
```

`decide()` runs the modes only when the page is already broken for the viewer, after it has set the payload to the generic card. It tries them in `MODE_ORDER`, takes the first map with a `mode` key, and skips names whose module is not in the build.

`ctx` holds:

| Key | Value |
|---|---|
| `user` | the logged-in `ApplicationUser`; never null here |
| `issue` | the `Issue`, or null when the key does not exist or the page names no issue |
| `key` | the issue's own key when it exists; otherwise the key from the URL in upper case, or null on a queues page without one |
| `pageKind` | `'browse'` or `'agent'` |
| `proj` | the issue's `Project`, or null |
| `pm` | the `PermissionManager` |
| `BROWSE` | `ProjectPermissions.BROWSE_PROJECTS` |

Helpers a mode can call, each to be listed in its `requires`:

| From | Helpers |
|---|---|
| `core/context`, always built | `mailOf(u)`, `hasAppAccess(u)`, `passesSecurity(issue, u)`, `isServiceDeskProject(proj)`, `hasRequestType(issue)`, `escalationFor(proj)` |
| policy modules, when built | `isInternal(u)`, `peopleFor(ctx)`, `jsmInCustomerContext { -> ... }`, `jsmPortalFor(who, proj, key)`, `isRequest(ctx)` |

The rules:

1. **Is this my case?** Cheapest checks first, and return null as soon as the answer is no.
2. **Audience gates before content.** Decide who may learn what the card says before you compute any of it. A viewer who fails a gate must get exactly the generic card, the same payload as for a key that does not exist, so return null. Never return a softer version of your card: that alone confirms the issue exists.
3. **Everything in the payload reaches the browser.** The payload is JSON in the page source, whether or not the client draws a value. Put in only what the viewer may learn: no e-mail address except the viewer's own, no ids the card does not need.
4. **An exception equals null.** `decide()` catches every `Throwable` from a mode and moves on to the next. A failure therefore degrades to the generic card, which is safe, and silent: test in the Script Console.
5. **Links.** Use root-relative URLs (`/browse/KEY`); the client adds the origin where a message needs a full link. Use `escalationFor(ctx.proj)` for the escalation link, and only on a card that already confirms the issue exists. The generic card uses `FALLBACK_URL` and must stay independent of the project.
6. **Read only, and cheap.** A mode must not change anything: `decide()` runs on every dead-end page view, and the decision tests rely on it only reading.

A card that reveals something new also needs a row in the table "What each card may say, and to whom" in [DESIGN.md](DESIGN.md).

## The policy contract

A policy is a closure under a fixed name that modes call. A profile chooses which module provides it, and a build holds exactly one provider per name.

| Name | Contract | Shipped modules |
|---|---|---|
| `isInternal(u)` | true only for a viewer inside your organisation. False for null, and false whenever its configuration is empty or cannot be met: it fails closed | `internal-mail-domain`, `internal-group` |
| `peopleFor(ctx)` | a list of at most two maps, `[name: <display name>, role: <label>]`, in display order. Only people who can act: active, not the viewer, not an automation account, holding Browse and Edit on the issue, with the issue editable for them | `people-reporter-assignee` |
| `jsmInCustomerContext`, `jsmPortalFor`, `isRequest` | the Service Management lookups, with classes reached by name at run time so that every build compiles without Service Management | `jsm` |

The `secured` mode calls `peopleFor` only after every gate has passed, and only when there is a field to suggest and `DISCLOSURE` allows people. It does not trim the list, so the limit of two is the policy's job. Role labels come from `TEXT` (`roleReporter`, `roleAssignee`, `roleAnd`). The client greets a person by first name only when exactly one is named.

A policy that requires both a group and a mail domain, for organisations that trust neither signal alone, can be put together from the two shipped policies. This sketch has not been run on an instance:

```groovy
// policy/internal-group-and-mail.groovy -- internal means: in one of
// INTERNAL_GROUPS, AND an e-mail address whose whole domain matches one of
// INTERNAL_MAIL_DOMAINS.
// provides: isInternal
import com.atlassian.jira.component.ComponentAccessor

def isInternal = { u ->
    if (u == null || INTERNAL_GROUPS.isEmpty() || INTERNAL_MAIL_DOMAINS.isEmpty()) { return false }
    def m = (u.getEmailAddress() ?: '').toLowerCase()
    int at = m.lastIndexOf('@')
    boolean mailOk = at > 0 && m.indexOf('@') == at &&
                     INTERNAL_MAIL_DOMAINS.any { p -> m.substring(at + 1) ==~ p }
    def gm = ComponentAccessor.getGroupManager()
    return mailOk && INTERNAL_GROUPS.any { g -> gm.isUserInGroup(u, g as String) }
}
```

Build it in place of the shipped policy (`modules = internal-group-and-mail people-reporter-assignee jsm portal share moved secured`) and test it with X01 to X03, plus an account that is in the group but outside the domain.

## Adding a mode, step by step

The template, [src/modes/_template.groovy](../src/modes/_template.groovy), defines a mode called `example`. The steps:

1. **Module.** Copy the template to `src/modes/example.groovy`. Keep `// provides: mode:example` and list every helper the closure calls in `// requires:`. Keep the three parts in order: is this my case, the gates, the content.
2. **`MODE_ORDER`.** Add `'example'` in `config/config.groovy`, or set `MODE_ORDER` in your profile. A name whose module is not in a build is skipped, so adding it to `config.groovy` does not affect other profiles. Position matters: the first mode that answers wins, and `portal` must stay before `share`.
3. **Client branch.** In `src/render/client.js`, `card()` has one branch per `d.mode`. Add yours before the final `else`, which draws the generic card. A payload without a branch falls through to that `else` and draws a generic card with missing links.

   ```js
       } else if (d.mode === 'example') {
         badge.innerHTML = ICON_HELP;
         box.appendChild(badge);
         box.appendChild(el('h1', 'jbh-title', T.exampleTitle));
         box.appendChild(el('p', 'jbh-text', fmt(T.exampleText, { key: d.issueKey })));

         var exOpen = el('a', 'jbh-btn jbh-btn-primary');
         exOpen.href = d.issueUrl;
         exOpen.appendChild(document.createTextNode(fmt(T.openKey, { key: d.issueKey })));
         actions.appendChild(exOpen);

         var exAsk = el('a', 'jbh-link', T.raiseRequest);
         exAsk.href = d.fallbackUrl;
         actions.appendChild(exAsk);

       } else {
   ```

   `var` is scoped to the whole of `card()`, which every branch shares, so give your variables names no other branch uses.
4. **Texts.** Add `exampleTitle` and `exampleText` to `config/text.en.groovy` and to every other text file you build. The whole `TEXT` map is sent to the browser.
5. **Render test.** Add a case per page to `CASES` in [tests/render_test.groovy](../tests/render_test.groovy), with synthetic values. If the payload carries free text, add hostile values too, as the existing "secured hostile values" case does.
6. **Decision test.** Add a case to [tests/decision_test.groovy](../tests/decision_test.groovy) where the mode answers, and at least one where a viewer who fails its gates gets `[mode: 'generic']`. Describe the account and the issue each case needs in the file's header comment.
7. **Profile.** Add `example` to `modules` in a profile, or write a new one.
8. **Build and check.**

   ```
   python build/assemble.py --all
   python build/strip_client_comments.py dist/<profile>.groovy --check
   python build/sync_tests.py --source dist/<profile>.groovy
   ```

   Then run the tests in the Script Console, `render_test` first: it needs no data and shows compilation errors. Before you commit, sync the tests back from the default source, `python build/sync_tests.py`: the repository's tests, and the CI check, use `dist/full.groovy`.
9. **Documentation.** The card's row in [DESIGN.md](DESIGN.md), any new value in [CONFIG.md](CONFIG.md), and the situation in [RECIPES.md](RECIPES.md).

A new CONFIG value goes into `config/config.groovy` as `final <Type> NAME = value` on one line, with a comment above it, so that profiles can override it.

## Rules for the client script

`src/render/client.js` is inlined into `writer.write($/ ... /$)` in `render/render.groovy`. Everything in it reaches every browser that gets a card.

- **Full-line comments only.** The build removes lines that start with `//`, and the check fails if any comment line survives in the assembled file. A `//` comment that shares a line with code is neither removed nor detected: it would ship, so do not write one. Do not use `/* ... */` either: the build does not remove it; on lines of its own it fails the check, and after code on the same line the check only warns.
- **No dollar signs except `$dataJson` and `$textJson`.** Inside a dollar-slashy string, `$name` is a Groovy interpolation and `$/` is an escaped slash, so any other dollar sign (a jQuery `$(`, a template literal, a `$` anchor in a regular expression) is rewritten by Groovy without an error. The check fails on it. Build strings with `+`.
- **Text nodes only.** Every server value and every text enters the page through `document.createTextNode`, which the `el()` helper uses. `innerHTML` is used only for the constant SVG icons defined in the script; never pass a payload value or a text through it. The JSON escaping of `<`, `>` and `&` protects the script block, not the page, so this rule still applies.
- **The DOM gate.** The card draws only into `.issue-error` (`/browse/`) or `#unlicensed-project-type` (agent view), hiding their stock children. A page that renders normally has neither node, so even a wrong server decision shows nothing. Do not add a host node that also exists on working pages.
- **Plain syntax.** Keep to the existing style: `var`, function expressions, no arrow functions, no `let` or `const`, no template literals. The script runs once per page, guarded by a window flag.

## Do not type the request object

`core/entry.groovy` reads the request like this:

```groovy
def req = ExecutingHttpRequest.get()
```

Up to Jira 10.x, `ExecutingHttpRequest.get()` returns a `javax.servlet.http.HttpServletRequest`; from Jira 11.0 it returns a `jakarta.servlet.http.HttpServletRequest` ([COMPATIBILITY.md](COMPATIBILITY.md)). A typed declaration with either import breaks on the other side of that line. Keep `def`, call only methods both types have (`getRequestURI()`), and do not import any `javax.servlet` or `jakarta.servlet` class. The same applies to any type whose package moved between Jira versions.

## Alternative: ScriptRunner script roots

ScriptRunner can load classes from a script root, `<jira home>/scripts`. The package must equal the directory path and the class name the file name: `<jira home>/scripts/util/demo/Bollo.groovy`, declaring `package util.demo`, is a class that any script, including a fragment, can import. The policies and modes could live there as classes, and the fragment would shrink to a few lines.

ScriptRunner's documentation ([Script Roots](https://docs.adaptavist.com/sr4js/latest/best-practices/write-code/script-roots/)) names the trap: "If you change a dependency class without triggering an update on the class or script that depends on it, the system will not recompile the dependent class or script. This can lead to outdated code being used." Its workaround is a tiny edit to the dependent file, such as a space in a comment or a blank line at the end, to trigger recompilation.

This project ships one assembled file instead, because:

- installing it needs only the fragment screen in ScriptRunner, not write access to the Jira home directory;
- what you paste is what runs, with no stale compiled dependency behind it;
- the client-comment check and the tests run against the exact text that is deployed;
- an upgrade or a rollback replaces one script.

If you do move modules into a script root: classes do not see the script's CONFIG variables, so pass values in as parameters; keep the client script in the fragment, where `writer` is bound, and keep checking it with `build/strip_client_comments.py`; and after every change to a class, touch the files that depend on it.
