# Compatibility

Verified on 28 September 2026.

This page lists everything the fragment depends on in Jira, Jira Service Management and ScriptRunner, and how each dependency is known to work. Every row carries one or more of three evidence labels:

| Label | Meaning |
|---|---|
| **verified against Javadoc** | The class and method are present, with the same signature, in the official Javadoc of every version checked (listed below). Javadoc proves that a call exists. It does not prove that the fragment behaves correctly. |
| **run in production by the author** | Exercised by version 1.0.0 on the author's production instance: Jira Data Center 9.x with ScriptRunner 8.x. With the `full` profile and the same CONFIG, 1.1.0 is meant to give every viewer the same card as 1.0.0, but the calls that are new in 1.1.0 do not carry this label until the 1.1.0 tests have been run there. |
| **not verified** | Neither of the above: stated from vendor documentation, from reasoning, or not known. |

## Summary

| Dependency | Range | Evidence |
|---|---|---|
| Jira Data Center | 8.0 to 11.x | verified against Javadoc 8.0.0 to 11.3.4; run in production by the author on 9.x |
| Jira Service Management (optional) | 4.0 to 11.x | verified against Javadoc 4.0.0 to 11.3.4; run in production by the author, with the Service Management release that pairs with Jira 9.x |
| ScriptRunner for Jira | the major that Marketplace pairs with your Jira: late 5.x builds and 6.x to 8.x for Jira 8, 8.x for Jira 9, 9.x for Jira 10, 10.x for Jira 11 | run in production by the author on 8.x; other majors not verified |
| Web panel location `atl.header.after.scripts` | undocumented | run in production by the author on 9.x; not verified on 10.x and 11.x |
| DOM hooks `.issue-error`, `#unlicensed-project-type` | undocumented | run in production by the author on 9.x; not verified elsewhere |

`Project.isArchived()` first appears in the Javadoc of Jira 7.9.0, so the fragment cannot run below 7.9 (verified against Javadoc). Nothing below 8.0 was checked further, and support is stated from 8.0.

## How it was checked

Jira Data Center Javadoc: 8.0.0, 8.20.0, 9.4.0, 9.12.0, 9.17.0, 10.3.0, 10.7.4 and 11.3.4, each class read signature by signature. On the date of the check, 10.7.4 and 11.3.4 were the latest published versions; 10.8.0 and 11.4.0 returned 404. The `jira-api` jars of 8.0.0 and 11.3.4 and the source of `atlassian-application-api` 2.0.0 to 4.0.0 were read as well. Two classes were checked separately for this page: `GroupManager` in 8.0.0, 9.12.0, 10.7.4 and 11.3.4, and `ExecutingHttpRequest` in 8.0.0, 10.7.4, 11.0.0 and 11.3.4.

Jira Service Management Javadoc: 4.0.0, 4.20.30, 4.22.6, 5.0.0, 5.12.0, 5.12.34, 5.17.5, 10.0.0, 10.3.19, 10.7.4, 11.0.0 and 11.3.4.

Atlassian's list of breaking changes for Jira 10 and Service Management 6 was read entry by entry; none touches a call below. Jira 11 removed `ComponentManager`, which the fragment does not use.

To re-check a row, open the class in the Javadoc of your version:

- Jira: `https://docs.atlassian.com/software/jira/docs/api/<version>/<package path>/<Class>.html`, for example <https://docs.atlassian.com/software/jira/docs/api/11.3.4/com/atlassian/jira/web/ExecutingHttpRequest.html>.
- Service Management: `https://docs.atlassian.com/jira-servicedesk/<version>/com/atlassian/servicedesk/api/<package>/<Class>.html`, for example <https://docs.atlassian.com/jira-servicedesk/11.3.4/com/atlassian/servicedesk/api/portal/PortalService.html>. The older pattern with `/server/<version>/` redirects to a page that does not exist.

## Jira platform API

Every call the assembled fragment makes, grouped by purpose.

### Request, viewer and issue

| API | Used for | Evidence | Notes |
|---|---|---|---|
| `ExecutingHttpRequest.get()`, `getRequestURI()` | reading the page's path | verified against Javadoc; run in production by the author | Returns `javax.servlet.http.HttpServletRequest` up to 10.7.4 and `jakarta.servlet.http.HttpServletRequest` from 11.0.0. The script keeps the result untyped (`def req`), so the same line works on both; on 11.x, not verified in production. See [EXTENDING.md](EXTENDING.md). |
| `ComponentAccessor.getJiraAuthenticationContext()`, `getLoggedInUser()` | the viewer | verified against Javadoc; run in production by the author | Called as a method, not through the property `loggedInUser` (see [Groovy](#groovy)). |
| `ComponentAccessor.getIssueManager()`; `IssueManager.getIssueObject(String)`, `isEditable(Issue)`, `isEditable(Issue, ApplicationUser)`, `getAllIssueKeys(Long)` | resolving the key; the "editable" gates; key history for the moved card | verified against Javadoc; run in production by the author | |
| `Issue.getKey()`, `getId()`, `getSecurityLevelId()`, `getProjectObject()`, `getReporter()`, `getAssignee()`, `getCustomFieldValue(CustomField)` | the issue's facts | verified against Javadoc; run in production by the author | |
| `ApplicationUser.getName()`, `getEmailAddress()`, `getDisplayName()`, `isActive()` | the viewer and the people on the card | verified against Javadoc; run in production by the author | |
| `ComponentAccessor.getComponent(Class)`, `getOSGiComponentInstanceOfType(Class)` | reaching Jira and Service Management components | verified against Javadoc; run in production by the author | |

### Permissions and application access

| API | Used for | Evidence | Notes |
|---|---|---|---|
| `ComponentAccessor.getPermissionManager()`; `PermissionManager.hasPermission(ProjectPermissionKey, Issue, ApplicationUser)` | does the page work for the viewer; can a named person browse and edit the issue | verified against Javadoc; run in production by the author | |
| `ProjectPermissions.BROWSE_PROJECTS`, `ProjectPermissions.EDIT_ISSUES` | permission keys | verified against Javadoc | New in 1.1.0; 1.0.0 built the same keys from strings. Not yet run in production. |
| `ApplicationRoleManager.hasAnyRole(ApplicationUser)`, with `ApplicationUser.isActive()` | application access: the viewer is an active account that is not a portal-only customer | verified against Javadoc; the equivalence with 1.0.0 measured on one instance (Jira 9.12) | New in 1.1.0. The Javadoc: "Returns true if the given user has been assigned to any ApplicationRole that is backed by a (potentially exceeded) license." It does not look at the account's status: on a census of 2 676 accounts, `hasAnyRole` answered true for 296 deactivated accounts where `hasPermission(GlobalPermissionKey.USE, user)` answered false, and agreed with `USE` for every active account. With `isActive()` in front of it the two agree for all 2 676. It replaces 1.0.0's `USE` check: `GlobalPermissionKey.USE` is `@Deprecated` since 7.0 in every checked version, and that overload of `hasPermission` is `@ExperimentalApi`. What either answers for a role whose licence is exceeded is not verified. Run X08 to X11 after upgrading. |
| `ApplicationAuthorizationService.canUseApplication(ApplicationUser, ApplicationKey)`, `ApplicationKey.valueOf('jira-servicedesk')` | does the viewer hold a Service Management agent licence (agent view only) | verified against Javadoc; run in production by the author | The Javadoc of `ApplicationKey` describes keys as `[a-zA-Z.]+`, without a hyphen; the library's source in 2.0.0 to 4.0.0 accepts `[a-zA-Z.-]+`, and `jira-servicedesk` is the key Atlassian's knowledge base uses. A failure counts as "not an agent", and the DOM gate still keeps the card off a page that works. |
| `ComponentAccessor.getPermissionSchemeManager()`; `PermissionSchemeManager.hasSchemePermission(ProjectPermissionKey, Issue, ApplicationUser, boolean)` | restricted card, gate 8: would the permission scheme alone let the viewer in | verified against Javadoc (signature); run in production by the author | `@Internal` in every checked version: Atlassian gives callers outside Jira no guarantee. The Javadoc does not say that the method leaves issue security out, which gate 8 relies on; that behaviour is confirmed only in production (X07). If the method disappears, the call throws, the mode gives no answer, and the viewer gets the generic card. |
| `PermissionSchemeManager.getSchemeFor(Project)`, `getPermissionSchemeEntries(Scheme, ProjectPermissionKey)`, and each entry's `getType()`, `getParameter()` | gate 8 through the suggested field | verified against Javadoc; run in production by the author | `getSchemeFor` is `@Nullable`; 1.1.0 checks for null. The type `'userCF'` equals `UserCF.TYPE` in every checked version. |
| `ComponentAccessor.getGroupManager()`; `GroupManager.isUserInGroup(ApplicationUser, String)` | the `internal-group` policy | verified against Javadoc (8.0.0, 9.12.0, 10.7.4, 11.3.4) | New in 1.1.0, in the `group-policy` profile only; not run in production. The `(String, String)` overload is deprecated; the policy uses the `ApplicationUser` one, which is not. |

### Issue security

| API | Used for | Evidence | Notes |
|---|---|---|---|
| `IssueSecurityLevelManager.getSecurityLevel(Long)`, `getUsersSecurityLevels(Issue, ApplicationUser)`; `IssueSecurityLevel.getId()`, `getSchemeId()`, `getName()` | the issue's level; does the viewer pass it | verified against Javadoc; run in production by the author | `getUsersSecurityLevels` is documented as "can be null"; 1.1.0 treats null as "no levels". |
| `IssueSecuritySchemeManager.getPermissionsBySecurityLevel(Long)`, and each grant's `getType()`, `getParameter()` | the level's grants, from which the candidate fields come | verified against Javadoc; run in production by the author | |

### Projects, fields and screens

| API | Used for | Evidence | Notes |
|---|---|---|---|
| `Project.getKey()`, `getName()`, `getProjectTypeKey().getKey()` | the project; is it a service desk | verified against Javadoc; run in production by the author | The type key `service_desk` is the one Atlassian's knowledge base uses. The `@Internal` constant `ProjectTypeKeys.SERVICE_DESK` is not used. |
| `Project.isArchived()` | restricted card, gate 6 | verified against Javadoc; run in production by the author | `@ExperimentalApi`. First in the Javadoc of 7.9.0 (absent from 7.8.0). A failure means the generic card. |
| `ComponentAccessor.getProjectManager()`; `ProjectManager.getProjectObjects()` | the service-desk projects, for the moved card's key history | verified against Javadoc; run in production by the author | |
| `ComponentAccessor.getCustomFieldManager()`; `CustomFieldManager.getCustomFieldObject(String)` | the request-type field; the candidate fields | verified against Javadoc; run in production by the author | |
| `CustomField.getId()`, `getIdAsLong()`, `getName()`, `getRelevantConfig(Issue)`, `getCustomFieldType().getKey()` | is the field a multi-user picker in context for this issue | verified against Javadoc; run in production by the author | The type key is matched with `endsWith(':multiuserpicker')`. The full key is a constant from 8.20 and is built from parts in 8.0; the suffix is the same in both. |
| `ComponentAccessor.getFieldScreenRendererFactory()`; `getFieldScreenRenderer(Issue, IssueOperation)` with `IssueOperations.EDIT_ISSUE_OPERATION`; `FieldScreenRenderer.getFieldScreenRenderTabs()`; `FieldScreenRenderTab.getFieldScreenRenderLayoutItems()`; `FieldScreenRenderLayoutItem.isShow(Issue)`, `getOrderableField()` | is the field on the issue's edit screen | verified against Javadoc; run in production by the author | |

## Jira Service Management API

Used only by the `jsm` policy module, and so only by the `full`, `jsm-only`, `secured-minimal` and `group-policy` profiles. The classes are reached by name at run time, so every build compiles where Service Management is absent.

| API | Used for | Evidence | Notes |
|---|---|---|---|
| `CustomerContextService.runInCustomerContext(NoExceptionsCallable<T>)` | running the portal and share checks the way the portal does | verified against Javadoc 4.0.0 to 11.3.4; run in production by the author | `@PublicApi`, unchanged. |
| `NoExceptionsCallable<V>.call()` | the body run in the customer context | verified against Javadoc 4.0.0 to 11.3.4; run in production by the author | `@PublicApi`, unchanged. 1.0.0 implemented it with a bare `java.lang.reflect.Proxy`; 1.1.0 uses Groovy's `asType`, which is not yet run in production. |
| `PortalService.getPortalForProject(ApplicationUser, Project)` | the portal of a request's project, for the viewer or for the reporter | verified against Javadoc 4.0.0 to 11.3.4; run in production by the author | `@PublicApi`, unchanged. Throws, rather than returning null, when there is no portal; the script catches that and treats it as "no portal". |
| `Portal.getId()` | the request's portal URL | verified against Javadoc 4.0.0 to 11.3.4; run in production by the author | `@PublicApi`, unchanged. |
| `Class.forName('com.atlassian.servicedesk.api...')` from a fragment script, without `@WithPlugin` | reaching the classes above | run in production by the author | Not documented. The portal and share cards work on the author's instance, so the classes resolve there. |

The Service Management release that pairs with Jira 10 is numbered 10.x: Atlassian renamed the planned 6.0 to 10.0, which is why some Atlassian pages speak of "Service Management 6" (source: Atlassian's compatibility matrix; a naming fact, not a test of this fragment).

## ScriptRunner

### Fragment type and the `writer` binding

| Fact | Source | Evidence |
|---|---|---|
| The fragment type is called "Show a web panel". Earlier documentation of this project called it "Custom web panel". | ScriptRunner documentation, [Web Panel](https://docs.adaptavist.com/sr4js/latest/features/fragments/web-panel/) | run in production by the author |
| A web panel script writes its HTML with `writer`: "You can use writer.write() to input HTML into your script." | the same page | run in production by the author |
| ScriptRunner's [binding variables](https://docs.adaptavist.com/sr4js/latest/best-practices/binding-variables/) page lists `issue`, `jiraHelper` and `log` for fragments, and does not mention `writer`. | the same | not verified: documentation gap only |
| On Jira 10, "Most simple scripts, like `writer.write("some HTML")`, will continue to work correctly. This breaking change only impacts users who used class implementations of the Atlassian WebPanel interface". The fragment is a simple `writer.write` script. | [Web Panel breaking change for Jira 10](https://docs.adaptavist.com/sr4js/latest/release-notes/breaking-changes/web-panel-breaking-change-deprecation-for-jira-10/) | not verified on Jira 10 |
| From ScriptRunner 8.0.0, Java 17 users may need the JVM flag `--add-opens=java.base/java.lang.reflect=ALL-UNNAMED`, for issues "when using reflection or accessing certain internal APIs". The fragment uses `Class.forName` and Groovy's `asType` on public interfaces only. If this ever bites, the Service Management cards fall back to the generic card. | [Breaking changes](https://docs.adaptavist.com/sr4js/latest/release-notes/breaking-changes/) | not verified |

### Which ScriptRunner for which Jira

The pairing columns are vendor data, taken from the Marketplace version history (276 Data Center versions on the date of the check). They are not tests of this fragment. The last column says how the fragment is known to work on that combination.

| Jira DC | Service Management | ScriptRunner majors per Marketplace | Latest ScriptRunner on 28 September 2026 | Groovy | This fragment |
|---|---|---|---|---|---|
| 8.x | 4.x | 5.x builds for Jira 8 (from 5.4.49-jira8); 6.x (up to Jira 9.2.1); 7.x (up to Jira 9.7.2); 8.x (from Jira 8.16 or 8.20) | 8.68.0, for Jira 8.20.0 to 9.17.5 | 2.4.15 (5.x), 2.5.11 (6.x), 3.0.12 (7.x), 4.0.7 (8.x) | not verified |
| 9.x | 5.x | 8.x; 7.x up to Jira 9.7.2 | 8.68.0, for Jira 8.20.0 to 9.17.5 | 4.0.7 (8.x), 3.0.12 (7.x) | run in production by the author (ScriptRunner 8.x) |
| 10.x | 10.x | 9.x only; 9.1.1 is the first, and 10.7.4 needs 9.23.0 or later | 9.44.0, for Jira 10.0.0 to 10.7.4 | Groovy 4 | not verified |
| 11.x | 11.x | 10.x only; 10.0.0 is the first | 10.18.0, for Jira 11.0.0 to 11.3.11 | Groovy 4 | not verified |

Sources:

- Marketplace REST API, ScriptRunner for Jira, Data Center versions: <https://marketplace.atlassian.com/rest/2/addons/com.onresolve.jira.groovy.groovyrunner/versions?hosting=datacenter>. One entry looks wrong: 8.40.0 lists Jira 8.2.0 as its minimum, probably a data error.
- Adaptavist release notes, [release 9.x](https://docs.adaptavist.com/sr4js/latest/release-notes/release-9.x/): "ScriptRunner 9.1.1 will only support Jira 10.0 and above and will not be backwards compatible with earlier Jira versions."
- Adaptavist release notes, [release 10.x](https://docs.adaptavist.com/sr4js/latest/release-notes/release-10.x/): "ScriptRunner 10.0.0 will only support Jira 11.0 and above and will not be backwards compatible with earlier Jira versions."
- Adaptavist's [compatibility page](https://docs.adaptavist.com/sr4js/latest/get-started/update-scriptrunner/compatibility-with-jira/) has no version table; for each Jira major it says "Only those versions marked as compatible in the Atlassian Marketplace will work".

### Groovy

| ScriptRunner | Groovy | Source | This fragment |
|---|---|---|---|
| 5.x | 2.4.15 | [release 6.x](https://docs.adaptavist.com/sr4js/latest/release-notes/release-6.x/): "upgraded from 2.4.15 to 2.5.11" | not verified |
| 6.x | 2.5.11 | the same | not verified |
| 7.x | 3.0.12 | [breaking changes](https://docs.adaptavist.com/sr4js/latest/release-notes/breaking-changes/): "Version 7.0.0+ Groovy was updated to 3.0.12" | not verified |
| 8.x | 4.0.7 | the same: "Version 8.0.0+ Groovy was updated to 4.0.7" | run in production by the author |
| 9.x, 10.x | Groovy 4 | the same page lists no Groovy update after 8.0.0 | not verified |

The [release notes for 8.x](https://docs.adaptavist.com/sr4js/latest/release-notes/release-8.x/) list five Groovy 4 breaking changes. None applies to the script:

1. **A property with both a getter and an "isser" now resolves to the isser.** ScriptRunner's own example is `jiraAuthenticationContext.loggedInUser`. The script calls getters explicitly (`getLoggedInUser()`, `getName()`) and uses property syntax only on maps and map entries (`ctx.issue`, `TEXT.roleReporter`) and for static constants (`ProjectPermissions.BROWSE_PROJECTS`).
2. **Legacy packages removed.** The only Groovy class the script imports is `groovy.json.JsonOutput`, which is not in a legacy package.
3. **Private fields accessed from closures.** The script declares no classes, so it has no private fields.
4. **`intersect()` changed.** Not used.
5. **`@Grab`.** Not used.

The client script sits in a dollar-slashy string (`$/ ... /$`), whose rules are the same in the Groovy 2.4.15 and 4.0.0 documentation: `$name` interpolates and `$/` is an escaped slash. The block holds exactly two interpolations, `$dataJson` and `$textJson`, and `build/strip_client_comments.py --check` fails on any other dollar sign.

### The Latin letter range in the client

The client's name tokenizer uses the character class `[^A-Za-z\u00C0-\u024F'-]`, written with escapes so that the source stays plain ASCII. Groovy may turn the escapes into the characters themselves when it reads the dollar-slashy string, or pass them through; the documentation checked does not say which. Either way the browser evaluates the same character class: a JavaScript regular expression reads the escape `\u00C0` and the character it stands for (capital A with grave) alike, on a page served as UTF-8. Evidence: not verified which form reaches the browser. The render test's "Latin Extended name" case prints the script as the browser receives it.

## Page hooks

| Hook | What it is | Evidence |
|---|---|---|
| Web panel location `atl.header.after.scripts` | where the fragment is rendered: in the page header's scripts, on every page | Not documented by Atlassian, and not in ScriptRunner's list of [web panel locations](https://docs.adaptavist.com/sr4js/latest/features/fragments/fragment-locations/web-panel-locations/). Seen in unofficial copies of Jira's web application: `head-resources.jsp` in 7.1.9, and the general, admin, error, login, message and displayError decorators (among others) in a 9.12.2 build. Run in production by the author on 9.x. Not verified on 10.x and 11.x: confirm it there with ScriptRunner's Fragment Locator ([Fragments](https://docs.adaptavist.com/sr4js/latest/features/fragments/)) before you rely on it. |
| `.issue-error` | the error block of `/browse/<KEY>` | Not documented. Run in production by the author on 9.x; not verified elsewhere. |
| `#unlicensed-project-type` | the "Snap! You can't view this page" section of the Service Management agent view | Not documented; Atlassian's knowledge base quotes only the text. Run in production by the author on 9.x; not verified elsewhere. |
| URL patterns `/browse/<KEY>` and `/projects/<P>/queues/...` | which requests the fragment answers | Run in production by the author on 9.x. |

If a Jira release removes a DOM hook, the script finds no node and draws nothing: the stock page stays.

Jira served under a context path (for example `https://jira.example.com/jira`): handled since 1.1.0, not verified on such an instance. The entry point strips the context path (`request.getContextPath()`, present in both the javax and the jakarta servlet API) before matching the URL patterns, and puts it back on every root-relative link in the payload (`issueUrl`, `portalUrl`, `helpCenter`, `fallbackUrl`, `myRequests`). Write the CONFIG links without the context path. 1.0.0 rendered nothing under a context path.

## Browsers

Evidence: run in production by the author, in the browsers the author's organisation uses; not verified against a browser matrix.

- The client script uses plain ES5 syntax: `var`, function expressions, no arrow functions, no `let` or `const`, no template literals.
- It uses `MutationObserver` for the agent view when the browser has it, and polling alone otherwise.
- The copy button uses the Clipboard API (`navigator.clipboard.writeText`) when present, falls back to `document.execCommand('copy')`, and then to a "copy it from the box above" label. Browsers offer the Clipboard API only on secure (HTTPS) pages, so on plain HTTP the fallback is what runs.
- The card's CSS uses flexbox with `gap`, `@keyframes`, `:focus-visible` and `prefers-reduced-motion`. A browser without `gap` or `:focus-visible` loses spacing or focus rings, not function.
