# Sterling agent plugin — adopt and maintain Sterling with a coding agent

**Status: shipped in v0.2.0 (2026-11-15), with the Copilot CLI and Codex evals and installs still to check.** See "Release" under Decisions.

## Goal

Ship an agent plugin that a developer installs once, at user scope, in GitHub
Copilot (CLI or VS Code), Claude Code or Codex, and then uses to add Sterling to new and
existing Gradle projects, fix what the gate finds, upgrade Sterling, and write
Java that passes the gate first time.

The plugin is done when a developer can run

```bash
claude plugin marketplace add balakmran/agent-plugins
claude plugin install sterling@balakmran
```

(or the Copilot or Codex equivalent), ask the agent to adopt Sterling in a
brownfield Spring Boot 4 service, and get a short series of reviewable commits
that end with the service on `STANDARD`, or on `LENIENT` with a report that
says exactly what blocks `STANDARD`.

## Scope

### In

- One agent plugin, `sterling`, under `agent-plugins/sterling/` in this
  repository, with a manifest per agent:
  - `plugin.json` (Agent Plugins 1.0; read by Copilot CLI, VS Code, and Codex
    as its portable manifest)
  - `.claude-plugin/plugin.json` (Claude Code)
  - `.codex-plugin/plugin.json` (Codex's own settings, and the manifest for
    Codex releases that predate Agent Plugins)
  - `skills/`
- Installation through each vendor's own plugin manager, at user scope only
- Four skills: `adopt-sterling`, `fix-sterling-findings`, `upgrade-sterling`,
  `sterling-conventions`, covering the whole v0.2.0 DSL (see "v0.2.0
  features in the skills")
- Scripts bundled in the skills that do the deterministic work: detection,
  task outcomes, coverage measurement
- `stlInfo --json` in the Gradle plugin: a stable, versioned JSON form of
  `stlInfo` that skills and CI read instead of the text
- `docs/guide/upgrading.md`: per-release upgrade notes, which
  `upgrade-sterling` reads
- Release wiring: release-please bumps all three manifests and each skill's
  `sterling-version`; a release job opens a PR on `balakmran/agent-plugins`
  pinning the new tag
- CI checks for the plugin on every PR
- Eval fixtures and a verify script per skill, run manually before a release
- A README section on installing and updating in each agent

### Out

| Item | Why |
|---|---|
| Agent hooks that format on every edit | Slow, and support differs by agent; enforcement belongs at the git layer (the pre-commit hook `adopt-sterling` installs) |
| An MCP server | Skills plus scripts cover it; nothing needs a long-running process |
| Subagents | Not needed for four sequential workflows, and support differs by agent |
| Committing skills into consumer repositories | The plugin is installed per developer, at user scope |
| A marketplace file in this repository | It lives in `balakmran/agent-plugins`; this repo only hands it the data in "Marketplace hand-off" |
| Upgrading Gradle or the JDK for a consumer | `adopt-sterling` stops and says what to upgrade |
| Generating new projects (Spring Initializr) | The "new project" path starts from an existing Gradle build |
| `gh skill`, `npx skills add`, `npx plugins add` | Skills-only or unverified installers; see "Installers" under Decisions. Every agent Sterling targets has its own plugin manager |
| Project- or workspace-scope installs | The plugin is per developer. No skill writes `.claude/settings.json`, `.github/copilot/settings.json` or `.codex/config.toml` plugin entries |
| Codex IDE extension | OpenAI's docs say it doesn't support plugins. README says so |
| Automated eval runs in CI | Evals run by hand before a release |
| Anything in the v0.1.0 plan's or `ROADMAP.md`'s later milestones | The skills use only the DSL and tasks that exist in the release they ship with |

## Formats verified (2026-11-14)

Read from each vendor's own docs on 2026-11-14. Several pages were long enough
that parts were read as summaries; those facts are marked *(summary)* and are
re-checked in the first implementation step.

### Copilot CLI

Sources:
[CLI plugin reference](https://docs.github.com/en/copilot/reference/cli-plugin-reference),
[Plugin and marketplace reference](https://docs.github.com/en/copilot/reference/plugin-and-marketplace-reference),
[Creating plugins](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-creating),
[Plugin marketplaces](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-marketplace),
[Agent Plugins spec 1.0.0](https://github.com/agentplugins/agent-plugins-spec/blob/main/spec/1.0.0.md),
[agent-plugins.org](https://agent-plugins.org/plugin-authors).

- **Manifest.** Agent Plugins format: `plugin.json` at the plugin root with
  `$schema` = `https://agent-plugins.org/schemas/1.0.0/plugin.schema.json`
  and `name` required. Allowed top-level fields are a closed set: `$schema`,
  `name`, `version`, `description`, `author`, `homepage`, `repository`,
  `license`, `keywords`, `extensions`. Unknown fields are reported and ignored.
  Component path fields (`skills`, `agents`, ...) are not allowed.
- **Lookup order.** Copilot also reads `.plugin/plugin.json`,
  `.github/plugin/plugin.json` and `.claude-plugin/plugin.json`; a root
  `plugin.json` targeting Agent Plugins wins over them.
- **Skills.** Immediate subdirectories of `skills/` with a `SKILL.md`; not
  configurable in the Agent Plugins format. `SKILL.md` follows the
  [Agent Skills spec](https://agentskills.io/specification).
- **Marketplace.** Looked up as `marketplace.json`, `.plugin/marketplace.json`,
  `.github/plugin/marketplace.json`, then `.claude-plugin/marketplace.json`.
  Entry sources: a relative path, `url`, or
  `{ "source": "github", "repo": "owner/repo", "ref": "...", "path": "...", "sha": "<40 hex>" }`.
  **No `git-subdir` type**; the `github` type with `path` does the same job.
- **CLI.** `copilot plugin marketplace add owner/repo[#ref]`,
  `copilot plugin install sterling@balakmran`, `copilot plugin update`,
  `copilot plugin list --json`. Installs under
  `~/.copilot/installed-plugins/<marketplace>/<plugin>`. **No validate
  command.**
- **VS Code** is covered in its own section below.

### Claude Code

Sources:
[Plugin marketplaces](https://code.claude.com/docs/en/plugin-marketplaces),
[Marketplace reference](https://code.claude.com/docs/en/plugins/marketplace-reference),
[Plugin manifest reference](https://code.claude.com/docs/en/plugins-reference),
[Plugin CLI reference](https://code.claude.com/docs/en/plugins/cli-reference),
[Plugin loading](https://code.claude.com/docs/en/plugins/loading),
[Skills](https://code.claude.com/docs/en/skills).

- **Manifest.** `.claude-plugin/plugin.json`; only `name` is required (kebab
  case). `validate` warns when `version`, `description` or `author` is missing.
- **Version and caching.** The manifest `version` names the cache directory
  (`cache/<marketplace>/<plugin>/<version>/`). A pinned version keeps users on
  the cached copy until the string changes, so every release must change it.
  Auto-update is off by default for third-party marketplaces; users run
  `claude plugin update`.
- **Skills.** `skills/<name>/SKILL.md`, invoked as `/sterling:<name>`.
  `${CLAUDE_SKILL_DIR}` and `${CLAUDE_PLUGIN_ROOT}` are substituted in the
  skill body *(summary)*; Sterling's skills don't use them (see Decisions).
- **Marketplace.** `.claude-plugin/marketplace.json`. Sources: relative path,
  `github` (`repo`, `ref`, `sha`; no `path`), `url`, **`git-subdir`
  (`url`, `path`, `ref`, `sha`) — confirmed**, `npm`, `archive`, `command`.
  With both `ref` and `sha`, the `sha` is checked out.
- **CLI.** `claude plugin marketplace add`, `claude plugin install
  <name>@<marketplace> [--scope user|project|local]` (default `user`),
  `claude plugin update`, **`claude plugin validate <path> [--strict]`**: exit
  0 on pass (with or without warnings), 1 on failure or any warning under
  `--strict`, 2 on an unexpected error *(summary)*.
- **Names.** `sterling` and `balakmran` hit none of the reserved names or
  prefixes.

### Copilot in VS Code

Sources:
[Agent plugins](https://code.visualstudio.com/docs/agent-customization/agent-plugins),
[Agent plugin reference](https://code.visualstudio.com/docs/agents/reference/agent-plugin-reference)
(read in part as a summary),
[Manage AI settings](https://code.visualstudio.com/docs/enterprise/manage-ai-settings) *(summary)*.

- **Manifests.** Auto-detected: Agent Plugins 1.0 (root `plugin.json` with
  the `$schema`), Copilot (root `plugin.json`), Claude
  (`.claude-plugin/plugin.json`), legacy `.plugin/plugin.json`. Codex's
  manifest isn't listed, and isn't needed: the root `plugin.json` is read.
- **Installing from a marketplace.** Agents window > Customizations (or
  *Chat: Open Customizations*) > Plugins > Browse. A marketplace is added under
  Configure Marketplaces > Add Marketplace (preview), or in the
  `chat.plugins.marketplaces` setting (experimental), as `owner/repo[#ref]`.
  First install from a new marketplace asks for trust. Needs
  `chat.plugins.enabled`.
- **Installing from source.** The "+" next to **User** or **Workspace** in the
  Plugins page, or *Chat: Install Plugin from Source*, takes "a Git repository
  URL ... or a local folder path". **No subdirectory or ref is documented**, so
  a URL to `balakmran/sterling` would not find `agent-plugins/sterling/`.
- **Scope.** User or Workspace, chosen per install; enablement is separate
  (Enable / Enable (Workspace)).
- **Shared with Copilot CLI.** VS Code "automatically discovers plugins that you
  install with the GitHub Copilot CLI" (`~/.copilot/installed-plugins/`).
- **Deep links.** `vscode://chat-plugin/add-marketplace?ref=<url-encoded>` and
  `vscode://chat-plugin/install?source=<url-encoded>&plugin=<name>`.
- **Updates.** "Check for and Apply Updates" on the Plugins page, or *Chat:
  Update Plugins*.
- **Cross-tool compatibility.** "VS Code also supports existing Copilot,
  Claude, and legacy OpenPlugin formats." Its marketplace example is a Claude
  Code marketplace (`anthropics/claude-code`), and the reference defers to
  Claude Code's marketplace docs for entries.
- **Not spelled out:** which marketplace file wins when a repository has both
  `.github/plugin/marketplace.json` and `.claude-plugin/marketplace.json`.
  It doesn't matter here: each file's entries use only source types its own
  format defines, so VS Code can read either. Checked once when the
  marketplace repo is set up.

### Codex

Sources (OpenAI's own docs; `developers.openai.com/codex/...` now redirects to
`learn.chatgpt.com/docs/...`):
[Build plugins](https://developers.openai.com/plugins/build/plugins),
[Submission and manifest fields](https://developers.openai.com/plugins/deploy/submission)
(read in part as a summary),
[Plugins](https://developers.openai.com/codex/plugins),
[Build skills](https://developers.openai.com/codex/skills) *(summary)*.

- **Manifests.** The current format is the Agent Plugins `plugin.json` at the
  plugin root. `.codex-plugin/plugin.json` "remain[s] supported as a
  compatibility fallback". When both exist:
  - Identity and components come from the root manifest: "Root identity and
    portable components remain canonical in either case." `skills/` is always
    discovered; a `skills` field in `.codex-plugin/plugin.json` applies only
    to packages without a recognized root manifest.
  - OpenAI settings (`interface`, `onboardingSkill`, `hooks`, ...) come from
    `extensions.com.openai` in the root manifest if that object exists, and
    then `.codex-plugin/plugin.json` is ignored ("the two files are not
    merged"). If it's absent, they come from `.codex-plugin/plugin.json`.
- **Codex-format required fields:** `name`, `version`, `description`,
  `author` (`author.name`), `interface`.
- **Skills.** Root `skills/<name>/SKILL.md`; `name` and `description` required.
The docs leave several details open, so these were settled from the
[openai/codex source](https://github.com/openai/codex/tree/main/codex-rs)
(`main` at `81b995a6c9a4`, 2026-11-14). Paths are under `codex-rs/`.

- **Marketplace file** (`core-plugins/src/marketplace.rs`). Codex takes the
  **first that exists** of `.agents/plugins/marketplace.json`,
  `.agents/plugins/api_marketplace.json`, `.claude-plugin/marketplace.json`,
  `.cursor-plugin/marketplace.json`. They are not merged, so an
  `.agents/plugins/marketplace.json` would hide the Claude file completely.
- **`git-subdir`** (same file): `url` and `path` required, `ref` and `sha`
  optional. `path` may or may not start with `./`, and may not contain `..`.
  `url` takes HTTPS, SSH or `owner/repo`.
- **`ref` with `sha`** (`core-plugins/src/loader.rs`): both are accepted; Codex
  checks out `sha` and verifies `HEAD` equals it (a full 40-character SHA),
  and ignores `ref`. Claude Code does the same.
- **Entry fields.** The parser needs only `name` and `source`; `policy`
  defaults to `installation: AVAILABLE`, `authentication: ON_INSTALL`;
  `category` is a free string. `description` and `version` in the entry are
  what Codex lists before install. The entry `name` must equal the plugin
  manifest's `name` (`core-plugins/src/store.rs`).
- **Plugin manifest** (`utils/plugins/src/plugin_namespace.rs`): a root
  `plugin.json` whose `$schema` is an agent-plugins.org URI, else
  `.codex-plugin/plugin.json`, `.claude-plugin/plugin.json`,
  `.cursor-plugin/plugin.json`, in that order. Skills default to `skills/`.
- **CLI** (`cli/src/plugin_cmd.rs`, `cli/src/marketplace_cmd.rs`):
  `codex plugin marketplace add owner/repo[@ref]`, `list`, `upgrade`,
  `remove`; **`codex plugin add sterling@balakmran`** installs (no
  `install` subcommand); `codex plugin list`, `codex plugin remove`. `/plugins`
  in the TUI opens the browser. The docs' pages don't list `codex plugin add`,
  but the CLI has it.
- **Scope** (`config/src/plugin_edit.rs`): installing writes to
  `~/.codex/config.toml` (user level), and caches under
  `~/.codex/plugins/cache/`.
- **No validate command. IDE extension:** "doesn't support plugins".

### Agent Skills spec

[agentskills.io/specification](https://agentskills.io/specification), which
all three vendors defer to for `SKILL.md`:

- `name`: 1–64 chars, `a-z0-9-`, no leading, trailing or double hyphen, equal
  to the directory name. `description`: 1–1024 chars.
- `metadata`: string-to-string map. `allowed-tools`: a space-separated string,
  experimental.

### Installers considered

| Installer | What it is | Why not |
|---|---|---|
| [`gh skill`](https://cli.github.com/manual/gh_skill) (GitHub CLI, preview) | Skills only; `--scope project` by default | VS Code's Agents window and Copilot CLI install the full plugin, so it adds nothing |
| [`npx skills add`](https://github.com/vercel-labs/skills) (Vercel Labs) | Skills only, many agents; project scope unless `-g`; needs Node 22.20+ | Anonymous telemetry on by default; installs skills without the manifests, so no versioned updates through the agent; `agent-plugins/sterling/skills/` is outside its standard search paths |
| `npx plugins add` | No such installer could be found. The npm page was unreachable, and agent-plugins.org says distribution "remain[s] under each client's control" | Unverified |
| Each vendor's plugin manager | Full plugin, versioned, user scope available everywhere | **Chosen** |

### What this means for the design

| Question | Answer |
|---|---|
| Do Copilot and Codex support `git-subdir`? | Codex: yes. Copilot CLI: not by that name, but its `github` source takes `path`, `ref` and `sha`, which is equivalent. No fallback layout is needed |
| One marketplace file for all agents? | Two. `.claude-plugin/marketplace.json` with `git-subdir` serves Claude Code and Codex (Codex reads it when there's no `.agents/plugins/` file), and VS Code reads the Claude format. Copilot CLI has no `git-subdir`, so it gets `.github/plugin/marketplace.json` with a `github` source and `path`, which it finds first |
| VS Code | Add the `balakmran/agent-plugins` marketplace and install at **User** scope, or install with Copilot CLI and let VS Code discover it. Install-from-source can't reach a subdirectory |
| Is `.codex-plugin/plugin.json` still needed? | Only to carry Codex's `interface` without putting `extensions.com.openai` in the shared root manifest, and for Codex releases that predate Agent Plugins. Kept, as the brief's layout has it |
| Codex install | `codex plugin marketplace add balakmran/agent-plugins`, then `codex plugin add sterling@balakmran` (or `/plugins`); user level |

## Decisions

Decisions given in the brief are not reopened; this table adds what the brief
left to the plan.

| Decision | Choice | Notes |
|---|---|---|
| Location | `agent-plugins/sterling/` | Not `plugins/`, to avoid confusion with the Gradle plugin. Folder, entry and manifest name are all `sterling` |
| Manifests | Root `plugin.json` (Agent Plugins 1.0), `.claude-plugin/plugin.json`, `.codex-plugin/plugin.json` | The root manifest never has `extensions.com.openai`: if it did, Codex would ignore `.codex-plugin/plugin.json`. That file carries Codex's required fields and `interface` |
| Marketplace | `balakmran/agent-plugins`, marketplace `name` `balakmran`; entries pinned to a release tag (and its commit SHA where the format allows both) | This repo ships no marketplace file |
| Installers | Each vendor's own plugin manager only: Claude Code, Copilot CLI, VS Code's Agents window, Codex's plugin browser | See "Installers considered". No `gh skill`, `npx skills` or `npx plugins` instructions |
| Install scope | **User only**, in every agent: `--scope user` for Claude Code (its default, written out anyway), Copilot CLI's `~/.copilot` store, **User** in VS Code, user config in Codex | Skills never write an agent's project or workspace plugin settings |
| Release | **v0.2.0**, with `stlInfo --json` in the same release | See below |
| Versioning | Agent plugin version = Sterling version. Each skill's frontmatter carries `metadata.sterling-version` | release-please bumps all of them (`extra-files`) |
| Skill → Sterling version | Each skill applies and upgrades to its own `sterling-version`, never "latest" | The skills and the Gradle plugin are tested together, like the tool pins |
| Self-contained skills | Each skill carries its own scripts; shared scripts are copied, and a test fails if copies differ | Agents load skills individually and resolve paths from the skill's own directory |
| Script paths in skills | In the body, relative to the skill directory (`scripts/detect.sh`), as the Agent Skills spec does | `${CLAUDE_SKILL_DIR}` only works in Claude Code |
| `allowed-tools` | Set on every skill, Claude Code syntax, pre-approving **only the skill's own scripts** by `${CLAUDE_SKILL_DIR}` path. Not `./gradlew`, not `git` | See "`allowed-tools`" below |
| v0.2.0 DSL | The skills use every v0.2.0 setting (see "v0.2.0 features in the skills"); `format.style` defaults to `GOOGLE` | The skills ship with 0.2.0 and must match it |
| Script language | Bash plus Gradle init scripts in Kotlin DSL; no `jq`, Python or Node | Consumers have Bash, Git and a JDK. Build scripts are Kotlin DSL already |
| Machine-readable state | `stlInfo --json` for configuration; init scripts for task outcomes; JaCoCo XML and SpotBugs XML for findings | Skills never decide from Gradle's log text, except javac diagnostics (below) |
| Error Prone and NullAway findings | Parsed from javac's diagnostic lines (`path:line: error: [Check] message`) | The format is javac's, stable for years. The 0.3.0 unified console format and Problems API would replace this |
| Branch | If the consumer is on its default branch, the skill creates `sterling/<skill>` first. Never pushes, never opens PRs | Trunk-based consumers review the commits as a PR |
| Working tree | Every skill stops on a dirty working tree | Its commits must contain only its own changes |
| Commit messages | Conventional Commits, `build:`/`style:`/`fix:`/`test:` as fits | Most consumers of this kind use them; harmless otherwise |
| `RestTemplate` in conventions | Not included | Sterling doesn't enforce it; `sterling-conventions` lists only what the gate can fail on. Spring advice belongs elsewhere |
| Eval fixtures | `agent-plugins/evals/`, outside the plugin directory | Nothing under it is installed by any agent |

### Release

`ROADMAP.md` has moved on since the brief: Checkstyle and `sterling-rules`
were moved from 0.2.0 to **0.5.0** on 2026-11-11. 0.2.0 is now "Adopt it on a
real Spring Boot 4 service", whose rule is "remove the reasons a Spring team
backs out in the first hour". `adopt-sterling` is that milestone's goal in
agent form, so the plugin belongs there.

- `stlInfo --json` is a `feat:`, so released alone it would cut 0.2.0 anyway
  (pre-1.0, `feat:` bumps the minor). It is step 1 of the work breakdown and
  merges early in 0.2.0, so the skills are written and tested against it.
- 0.2.0 also brings `format.style`, `format.ratchetFrom`, Spring-aware NullAway
  defaults, coverage of extra test suites and the console summaries. The
  skills are written last, against the DSL as merged, use all of it (next
  section), and a test (below) fails if a skill names a task or setting that
  doesn't exist.

### v0.2.0 features in the skills

From [`v0.2.0.md`](v0.2.0.md), which specifies each feature. If a feature slips out of 0.2.0, its row
moves with it.

| v0.2.0 feature | `adopt-sterling` | `upgrade-sterling` (from 0.1.0) | Others |
|---|---|---|---|
| `format.style` (`GOOGLE`, `AOSP`) | Plan offers both; default `GOOGLE`. Detection reports whether most Java files use 4-space indentation, and if so the plan says `AOSP` would shrink the reformat. The user chooses | Keeps the project's style | `sterling-conventions` reads the style from `stlInfo --json` |
| `format.ratchetFrom` | Offered as the alternative to the reformat commit: with it, steps 2–3 are skipped and only changed files are formatted. Default stays the reformat commit | Leaves it as set | — |
| Spring-aware NullAway defaults (`CheckContracts`, `HandleTestAssertionLibraries`; no `CustomContractAnnotations`, which NullAway doesn't need) | Removes the project's own copies of options Sterling now sets, and a redundant `CustomContractAnnotations` | Removes the same, and the `Application*` exclude 0.1's README suggested | `fix-sterling-findings` doesn't suppress NullAway findings that these defaults resolve |
| Spring Boot reactions (application class out of coverage) | Doesn't add a `coverage.excludes` entry for the application class; removes an existing one Sterling makes redundant | Removes such an entry | — |
| Lombok | Detected and reported; nothing to configure | — | — |
| Coverage of every `Test` task (`coverage.testTasks` narrows it) and generated classes | Thresholds are measured from the report Sterling produces, so they include `integrationTest`; excludes for generated classes become redundant and are removed | Re-measures if the counted tasks changed, and reports the change; never lowers thresholds below the new measurement without saying so | — |
| Supported tool version overrides | A project pin older than or equal to Sterling's is removed; a newer one is kept and reported | Removes overrides the new Sterling version meets | — |
| Console summary for `stlSec` and `stlCov` | — | — | `fix-sterling-findings` still reads the XML reports; the summary is for people |
| Multi-module recipe (`build-logic` convention plugin) | Multi-module builds get Sterling through the recipe | — | — |
| Migration page (from Spring's NullAway snippet, from `build-logic`) | `reference/duplicate-config.md` mirrors it, and a test checks they list the same items | — | — |
- If the rest of 0.2.0 is ready before the skills, release it; the plugin then
  ships with the next Sterling release, since the versions are shared.
- Cost of the shared version: a skills-only fix releases a Sterling patch whose
  Gradle plugin is unchanged. Accepted; it keeps "which skills go with which
  Sterling" trivial.

## Behaviour spec

### `stlInfo --json` (Gradle plugin)

`./gradlew -q stlInfo --json` prints one compact JSON object per project, on
one line each (NDJSON), at quiet level. Without `--json`, `stlInfo` prints the
text it prints today.

- `--json` is a task option (`@Option`) on a `Property<Boolean>` annotated
  `@Input`. It works with task selectors across projects, as `test --tests`
  does, and with the configuration cache.
- The JSON is written by a small hand-written writer with string escaping and
  a unit test: no new dependency (`buildHealth`).
- Report paths come from the tools' tasks as providers, made relative to the
  project directory. Paths use `/`.
- Validation runs as it does for the text form: a threshold of 120 or a bogus
  `sterling.profile` fails the task.

Schema version 1:

```json
{
  "schemaVersion": 1,
  "sterling": "0.2.0",
  "project": ":api",
  "profile": "STANDARD",
  "profileProperty": "standard",
  "attachToCheck": true,
  "daemonJvm": 25,
  "javaToolchain": { "active": true, "version": 25 },
  "format": {
    "enabled": true,
    "style": "GOOGLE",
    "versions": { "google-java-format": "2.0.0", "spotless": "8.11.0" }
  },
  "errorProne": {
    "enabled": true,
    "version": "2.52.0",
    "warningsAsErrors": false,
    "disabledChecks": ["UnusedVariable"]
  },
  "nullAway": {
    "enabled": true,
    "active": true,
    "version": "0.14.3",
    "jspecify": "1.0.1",
    "jspecifyMode": false
  },
  "spotbugs": {
    "enabled": true,
    "version": "4.10.5",
    "effort": "DEFAULT",
    "reportLevel": "MEDIUM",
    "excludeFilter": "config/spotbugs-exclude.xml",
    "reports": { "xml": "build/reports/spotbugs/main.xml", "sarif": "build/reports/spotbugs/main.sarif" }
  },
  "coverage": {
    "enabled": true,
    "version": "0.8.16",
    "instruction": 80,
    "branch": 70,
    "excludes": ["**/*Application*"],
    "reports": { "xml": "build/reports/jacoco/test/jacocoTestReport.xml" }
  }
}
```

- Keys are the DSL's names (`errorProne`, `nullAway`, `warningsAsErrors`), so
  a skill maps a key to a setting without a table.
- `profileProperty` is the raw `sterling.profile` value, or `null` when unset.
  It tells `adopt-sterling` whether the profile lives in `gradle.properties`.
- `nullAway.active` is `enabled && errorProne.enabled`, the same rule as the
  text form's "off (Error Prone off)".
- Without `java`: `"javaToolchain": { "active": false, "reason": "java plugin
  not applied" }`; tool blocks still show their settings; `reports` is absent.
- `excludeFilter` is `null` when unset. `format.style` and any other 0.2.0
  setting appear only once that setting exists; the final list is whatever the
  0.2.0 DSL has when this step merges, plus each new setting as it lands.
- **Compatibility.** Adding a key keeps `schemaVersion` 1. Removing or renaming
  one, or changing a value's type, bumps it and is a breaking change in the
  changelog. Skills check `schemaVersion == 1` and stop with a clear message
  otherwise.
- `docs/guide/configuration.md` documents the schema; `DocsTest` checks every
  key appears there.

### `docs/guide/upgrading.md`

One section per minor release, newest first: settings renamed or removed and
their replacement, defaults that changed, tool bumps that can change findings
(rule 7 in `ROADMAP.md`), and whether google-java-format's version changed.
Written by hand in the release PR. `upgrade-sterling` reads it from the target
tag (`https://raw.githubusercontent.com/balakmran/sterling/v<version>/docs/guide/upgrading.md`),
not from the changelog, whose entries are commit subjects.
`DocsTest` fails if the current minor has no section.

### Plugin layout

```
agent-plugins/
├── sterling/                         # the installable plugin
│   ├── plugin.json                   # Agent Plugins 1.0 (Copilot, Codex)
│   ├── .claude-plugin/plugin.json    # Claude Code
│   ├── .codex-plugin/plugin.json     # Codex overlay: interface fields
│   └── skills/
│       ├── adopt-sterling/
│       │   ├── SKILL.md
│       │   ├── reference/            # plan template, duplicate-config table
│       │   └── scripts/
│       │       ├── detect.sh
│       │       ├── detect.init.gradle.kts
│       │       ├── outcomes.sh       # copy; see "Self-contained skills"
│       │       ├── outcomes.init.gradle.kts
│       │       └── coverage.sh
│       ├── fix-sterling-findings/
│       │   ├── SKILL.md
│       │   ├── reference/            # per-tool fix guidance
│       │   └── scripts/              # outcomes.*, findings.sh
│       ├── upgrade-sterling/
│       │   ├── SKILL.md
│       │   └── scripts/              # outcomes.*
│       └── sterling-conventions/
│           └── SKILL.md
└── evals/                            # not installed; see Testing
    ├── fixtures/
    ├── verify/
    └── run.sh
```

Manifests (version shown as release-please leaves it):

```jsonc
// plugin.json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "sterling",
  "version": "0.2.0",
  "description": "Adopt, fix and upgrade Sterling, the Java 21+ Gradle quality gate, with your coding agent.",
  "author": { "name": "Balakumaran Manoharan", "url": "https://github.com/balakmran" },
  "homepage": "https://github.com/balakmran/sterling",
  "repository": "https://github.com/balakmran/sterling",
  "license": "Apache-2.0",
  "keywords": ["java", "gradle", "error-prone", "nullaway", "spotbugs", "jacoco"]
}
```

`.claude-plugin/plugin.json` has the same fields minus `$schema`.
`.codex-plugin/plugin.json` has Codex's required fields, `name`, `version`,
`description`, `author` and `interface` (`displayName: "Sterling"`,
`shortDescription`, `developerName`, `category`, `websiteURL`), plus
`skills: "./skills/"` for Codex releases that don't read the root manifest.

Every `SKILL.md` starts:

```yaml
---
name: adopt-sterling
description: >-
  Add Sterling (dev.balakmran.sterling), the Java 21+ Gradle quality gate, to a
  new or existing Gradle project ...
license: Apache-2.0
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/detect.sh) Bash(${CLAUDE_SKILL_DIR}/scripts/outcomes.sh:*) Bash(${CLAUDE_SKILL_DIR}/scripts/coverage.sh:*)
metadata:
  sterling-version: "0.2.0" # x-release-please-version
---
```

#### `allowed-tools`

Checked 2026-11-14:

| Agent | Support | Source |
|---|---|---|
| Claude Code | Honoured as pre-approval for the turn, not a restriction. Space- or comma-separated string, or YAML list. `${CLAUDE_SKILL_DIR}` and `${CLAUDE_PLUGIN_ROOT}` are expanded in its `Bash(...)` rules; the documented example is `Bash(${CLAUDE_SKILL_DIR}/scripts/render.sh *)`. `Bash(x:*)` equals `Bash(x *)` | [Skills](https://code.claude.com/docs/en/skills), [Permissions](https://code.claude.com/docs/en/permissions) |
| Copilot CLI | Honoured as auto-approval, in its own vocabulary (`shell(git:*)`, `read(...)`), comma-separated or YAML array. GitHub warns: "When in doubt, omit `shell` and `bash`". Since 1.0.100, ambiguous lists prompt instead of granting. No variable expansion documented | [Add skills](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-skills), [CLI reference](https://docs.github.com/en/copilot/reference/cli-command-reference), [changelog](https://github.com/github/copilot-cli/blob/main/changelog.md) |
| VS Code | Not documented | [Agent skills](https://code.visualstudio.com/docs/copilot/customization/agent-skills) |
| Codex | Ignored: the parser reads only `name`, `description` and `metadata.short-description` (`codex-rs/skills/src/parser.rs`) | openai/codex source |

No one value means the same thing in every agent, so the field is written
for Claude Code, where it has a defined meaning, and is harmless elsewhere:
Copilot CLI finds no rule in its vocabulary and prompts, Codex ignores it.

- **Only the bundled scripts are pre-approved.** `./gradlew *` would be close
  to an open shell: `--init-script` or a task from a build script runs
  arbitrary code. `git` is left out so the user sees every commit.
- **The scripts accept a fixed set of arguments.** `outcomes.sh` takes only
  Sterling's tasks, `compileJava`, `compileTestJava`, `test`,
  `jacocoTestReport`, `-Psterling.profile=<profile>`, `--continue` and `-q`,
  and rejects anything else (`-I`, `--init-script`, `-D`, other tasks), so
  pre-approving it doesn't pre-approve Gradle.
- **The body names the path that matches the rule:** "Run
  `scripts/outcomes.sh` from this skill's directory (Claude Code:
  `${CLAUDE_SKILL_DIR}/scripts/outcomes.sh`)". Other agents read the relative
  path.
- No Copilot `shell(...)` rules: GitHub's own advice is to leave shell out.

### Guardrails (every skill)

1. Stop on a dirty working tree; create `sterling/<skill>` when on the
   default branch; never push.
2. Never change business logic, except `fix-sterling-findings`. Build files,
   `gradle.properties`, `.git-blame-ignore-revs`, hook config and the
   formatter's output are the only changes the other skills make.
3. Never lower a coverage threshold below the measured value, never disable a
   check or a tool, and never add a suppression or exclude, without stating
   which, where and why in the final report.
4. Decide from task outcomes (`outcomes.sh`), `stlInfo --json` and report
   files, never from Gradle's log text.
5. Never upgrade Gradle, the wrapper, the toolchain or the daemon JVM; never
   change the Sterling version except to the skill's `sterling-version`.
   Never write an agent's project or workspace plugin settings.
6. Never use `--no-verify`, `-x <task>` or `--offline` tricks to get a gate to
   pass.
7. Show the plan and wait for confirmation before the first change (agents
   without interactive confirmation get it in the prompt; see Testing).
8. End with a report: what was run, the commits made (SHA and subject), the
   final `stlInfo`, and anything left off or suppressed, with why.

### Shared scripts

- **`outcomes.sh <gradle args...>`** runs `./gradlew` with
  `outcomes.init.gradle.kts` and writes `build/sterling-agent/outcomes.json`
  at the root: `{"exitCode": 1, "tasks": [{"path": ":stlSec", "outcome":
  "FAILED"}, ...]}`, with outcomes `SUCCESS`, `UP_TO_DATE`, `FROM_CACHE`,
  `SKIPPED`, `NO_SOURCE`, `FAILED`.
- **How the init script works.** In `beforeSettings { }` it applies a
  `Plugin<Settings>` defined in the script. That plugin is injected with
  `BuildEventsListenerRegistry` (public API, documented as injectable into
  plugins) and registers a build service from `gradle.sharedServices` with
  `onTaskCompletion(...)`. The service collects each `TaskFinishEvent` and
  writes the JSON in `close()`. Only public API, no `TaskExecutionListener`,
  no `buildFinished`.
- **Why settings, not `Plugin<Gradle>`.** Gradle documents service injection
  for project and settings plugins; it says nothing either way about
  `Plugin<Gradle>` from an init script.
- **Checked 2026-11-14** with a prototype on Gradle 9.0.0 and 9.9.1, with
  `--configuration-cache`: `SUCCESS`, `UP_TO_DATE` on a re-run with the cache
  entry reused, and `FAILED` beside `SUCCESS` under `--continue`. The
  init script is part of the configuration cache key, so the project's own
  cache entries are untouched. The script tests below keep it honest on both
  Gradle versions.
- Prints the path of the JSON and the exit code; the skill reads the JSON.

### `adopt-sterling`

Triggers on requests to add, set up or adopt Sterling, or "add a quality
gate / Error Prone / NullAway" to a Gradle Java project.

**1. Detect.** `scripts/detect.sh` prints one JSON document. Two phases:

- *Static, no Gradle run:* Gradle version from
  `gradle/wrapper/gradle-wrapper.properties`; daemon JVM from
  `gradle/gradle-daemon-jvm.properties`, else `JAVA_HOME`/`java -version`;
  git state (clean, current and default branch, hook manager: prek/pre-commit
  config, Husky, Lefthook, `core.hooksPath`, plain `.git/hooks/pre-commit`);
  `.git-blame-ignore-revs`; version catalog; `buildSrc`/`build-logic`;
  whether Sterling is already applied and at which version.
- *Stops here* with a clear message when there is no wrapper, Gradle is below
  9.0, or the daemon JVM is below 21.
- *Gradle phase:* `./gradlew -q --init-script detect.init.gradle.kts
  --no-configuration-cache sterlingDetect`. Per project: path, applied plugin
  IDs (Spotless, `net.ltgt.errorprone`, `com.github.spotbugs` or `-base`,
  `jacoco`, `checkstyle`, `pmd`, `org.springframework.boot`), toolchain
  language version, declared dependencies that matter (Lombok, MapStruct,
  `error_prone_core`, `nullaway`, `jspecify`), generated source directories,
  existing JaCoCo violation rules and `spotless { java { } }` steps (as far as
  the public APIs expose them), Java source count, `@NullMarked` packages,
  and whether most Java files are indented with 4 spaces (for `format.style`).
- Stops when any Java project's toolchain is below 21. Never offers to upgrade.

**2. Classify and plan.** *New* when no Java project has more than a handful
of main sources (threshold set during implementation) or the user says so;
*existing* otherwise. The plan shown to the user lists: where the plugin goes
(version catalog entry and `plugins { }`, or the `build-logic` convention
plugin from Sterling's multi-module recipe), `format.style` (`GOOGLE` unless
the user picks `AOSP`), reformat commit or `format.ratchetFrom`, the commits
below, the duplicate config to remove, the hook, and what detection couldn't
tell. Waits for confirmation.

**3a. New project.**

1. Add the plugin at the skill's `sterling-version` with `STANDARD` (no
   `sterling.profile`), run `stlFmt`, commit.
2. Run `outcomes.sh stlCheck`. On failure, report the failing tasks and point
   to `fix-sterling-findings`; don't lower anything.
3. Install the hook (below), commit if it's a tracked file.

**3b. Existing project.** One commit each, in this order:

1. **Add Sterling at `LENIENT`.** Plugin plus `sterling.profile=lenient` in
   `gradle.properties`. Before committing, `outcomes.sh compileJava
   compileTestJava` must succeed: Error Prone's error-level findings fail
   compilation on every profile. If it fails, stop and hand over to
   `fix-sterling-findings` (with the user's go-ahead), since this skill doesn't
   touch code.
2. **Reformat.** `stlFmt`, committed alone as `style: reformat with
   google-java-format <version>`. Verified to be formatting-only: the commit's
   tree must equal its parent with `stlFmt` applied, and `stlFmtCheck` must
   pass.
3. **Ignore the reformat in blame.** Append the reformat SHA to
   `.git-blame-ignore-revs` (created if missing) and commit. The report says to
   run `git config blame.ignoreRevsFile .git-blame-ignore-revs`; the skill
   doesn't change git config.
4. **Remove tool config Sterling now owns**, from the table in
   `reference/duplicate-config.md`, which mirrors `docs/guide/adopting.md`:
   - `com.github.spotbugs` replaced by Sterling's; its effort and report level
     moved to `sterling { spotbugs { } }`; an exclude filter kept and moved to
     `spotbugs.excludeFilter`
   - `googleJavaFormat()`, `formatAnnotations()` and, on Java only,
     `removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`
     from `spotless { java { } }`; other steps kept
   - a consumer `errorprone("...error_prone_core...")`, `nullaway` or
     `jspecify` declaration and `options.errorprone` settings that Sterling
     sets (`NullAway` severity, `OnlyNullMarked`); disabled checks moved to
     `errorProne.disable(...)`; options Sterling doesn't set kept
   - JaCoCo `violationRules` replaced by `sterling.coverage` (next commit),
     excludes moved to `coverage.excludes`
   - Checkstyle and PMD left alone and listed in the report

   After the change, `outcomes.sh stlInfo stlFmtCheck compileJava` must
   succeed and `stlInfo --json` must show what was moved.
5. **Set coverage thresholds.** Run `outcomes.sh test jacocoTestReport
   -Psterling.profile=standard` so the agent runs, then `scripts/coverage.sh`
   reads each module's JaCoCo XML (path from `stlInfo --json`) and prints the
   instruction and branch percentages, rounded down. Each module gets its own
   `sterling { coverage { instruction = N; branch = M } }` in its own build
   script, even when a convention plugin applies Sterling. A module with no
   branches gets `branch = 0`, said in the report.
   Thresholds above the measured value are never set; any previous
   project-configured minimum higher than the measurement is reported, not
   kept (the build was not enforcing it, or it would have failed).
6. **Move to `STANDARD`** only if `outcomes.sh stlCheck
   -Psterling.profile=standard` succeeds: remove the property (or set
   `standard`). Otherwise stay on `LENIENT`, and the report lists the failing
   tasks and their findings' counts, and points to `fix-sterling-findings`.
7. **Pre-commit hook** running `./gradlew stlFmtCheck -q`, committed as
   config so the whole team gets it:
   - Husky or Lefthook already in use: add the command to its pre-commit
     config (a second hook manager would fight it over `core.hooksPath`)
   - otherwise: add the `stl-fmt-check` hook from `docs/guide/adopting.md` to
     `.pre-commit-config.yaml`, creating it if missing
   - the skill never runs `prek install` or edits `.git/hooks`; the report
     asks the user (and the team) to run `prek install`

**4. Report** per guardrail 8, plus the next steps from
`docs/guide/adopting.md` (marking packages `@NullMarked`, `STRICT` in CI).

### `fix-sterling-findings`

Triggers on a failed `stlCheck`, `stlLint`, `stlSec` or `stlCov`, or on "fix
the Sterling / Error Prone / NullAway / SpotBugs / coverage findings".

1. Run `outcomes.sh stlCheck --continue` and read which tasks failed.
2. `scripts/findings.sh` collects findings into one list:
   - compile tasks: javac diagnostic lines with `[CheckName]` (NullAway's are
     `[NullAway]`)
   - `spotbugsMain`: the XML report (path from `stlInfo --json`)
   - `jacocoTestCoverageVerification`: JaCoCo XML totals against the
     thresholds, and the least-covered classes
   - `spotlessCheck`: no list; the fix is `stlFmt`
3. Fixes in that order (formatting last, so its diff is the formatter's):
   compile errors, SpotBugs, coverage, then `stlFmt`. Per-tool guidance lives
   in `reference/`:
   - NullAway: fix the nullness (annotate with `org.jspecify.annotations.Nullable`,
     check, or restructure); never mark a package `@NullMarked` or remove it to
     make findings go away
   - coverage: write tests for uncovered code; never add `coverage.excludes`
     or a `@Generated` annotation to dodge it
4. Suppressing is a last resort: `@SuppressWarnings("CheckName")` with a
   comment giving the reason, or a SpotBugs `<Match>` in the project's
   `spotbugs.excludeFilter` with a comment. Each one is listed in the report.
5. Re-run until `stlCheck` succeeds, or stop after a bounded number of rounds
   and report what's left.
6. Commits: one per tool, `fix:` for code fixes, `test:` for new tests.

### `upgrade-sterling`

Triggers on "upgrade Sterling" or on a Sterling version older than the skill's.

1. Read the current version: `stlInfo --json` when the project's Sterling
   supports it, otherwise the version in the catalog or `plugins { }` block
   (0.1.0 has no `--json`).
2. Stop if the project is already at or above the skill's `sterling-version`;
   tell the user to update the agent plugin for a newer one.
3. Read `docs/guide/upgrading.md` at the target tag; show the plan.
4. Commit 1, `build: upgrade Sterling to <version>`: the version bump plus
   every DSL change the upgrade notes call for.
5. If google-java-format's version changed (old vs new `stlInfo --json`):
   commit 2, `stlFmt` alone, then its SHA in `.git-blame-ignore-revs`, as in
   `adopt-sterling`.
6. Run `outcomes.sh stlCheck`. New findings from tool bumps are reported
   with a pointer to `fix-sterling-findings`; the skill doesn't fix them or
   lower the profile.

### `sterling-conventions`

Triggers when the agent writes or edits Java in a Gradle project that applies
`dev.balakmran.sterling`; does nothing otherwise. Each rule names what fails if
it's broken, and the list holds only rules the gate enforces at the profile in
`stlInfo --json`:

- **After editing:** run `./gradlew stlFmt stlLint -q`; never hand-format to
  google-java-format, never edit `build/generated`.
- **NullAway (in `@NullMarked` code):** `@Nullable` from
  `org.jspecify.annotations` only, on the type (`@Nullable String`,
  `String @Nullable []`); a nullable value is checked before it's
  dereferenced; non-null fields are assigned in every constructor; a new
  package in a codebase whose packages are `@NullMarked` gets a
  `package-info.java` with `@NullMarked`. In JSpecify mode (`STRICT`):
  nullness in type arguments, `List<@Nullable String>`.
- **Constructor injection** with `final` fields, not field injection: it
  satisfies NullAway's initialization check without suppression, and
  Sterling's SpotBugs filter already excludes the `EI_EXPOSE_REP` findings it
  would otherwise cause.
- **Error Prone:** the default checks at error level fail every build; at
  `STRICT`, warnings do too. The skill lists the ones agents commonly trip,
  checked against Error Prone's bug-pattern docs at the pinned version during
  implementation (candidates: `UnusedVariable`, `MissingOverride`,
  `ReferenceEquality`, `DefaultCharset`, `StringSplitter`,
  `JavaTimeDefaultTimeZone`, `JdkObsolete`, `OperatorPrecedence`).
- **SpotBugs (from `STANDARD`):** explicit charsets, no ignored return values
  of immutable-type methods, no mutable static fields; the list is checked
  against SpotBugs's patterns at the pinned version.
- **Coverage (from `STANDARD`):** new code ships with tests that keep the
  module above its thresholds.
- **Suppressions:** never without a comment giving the reason.

## Marketplace hand-off

What `balakmran/agent-plugins` needs from this repo. That repo owns its files,
its own CI, and a `scripts/pin.sh <entry> <tag> <sha>` that updates every
marketplace file.

| File | Read by | `sterling` entry `source` |
|---|---|---|
| `.claude-plugin/marketplace.json` | Claude Code, Codex, VS Code | `{"source": "git-subdir", "url": "https://github.com/balakmran/sterling.git", "path": "agent-plugins/sterling", "ref": "v0.2.0", "sha": "<40 hex>"}` |
| `.github/plugin/marketplace.json` | Copilot CLI (checked before `.claude-plugin/`); VS Code may read it too | `{"source": "github", "repo": "balakmran/sterling", "path": "agent-plugins/sterling", "ref": "v0.2.0", "sha": "<40 hex>"}` |

- Both files give `ref` (readable) and `sha` (immutable); Claude Code, Codex
  and Copilot all check out the `sha`.
- **No `.agents/plugins/marketplace.json`**: Codex would read it instead of
  the Claude file. **No root `marketplace.json` or `.plugin/marketplace.json`**:
  Copilot would read it before `.github/plugin/`.
- Entry fields from this repo: `name: "sterling"` (must equal the manifest
  `name`, or Codex refuses it), `description` and `version` (what Codex lists
  before install), `category: "development"`, `keywords`. No `policy`: Codex's
  defaults (`AVAILABLE`, `ON_INSTALL`) are what Sterling wants, and Claude
  Code's `validate --strict` would warn on an unknown key.
- **Data handed over on each release:** the tag (`v<version>`), the tag's
  commit SHA, the version.
- **Fallback** if a source type proves unusable at implementation time: the
  marketplace repo vendors a copy of `agent-plugins/sterling/` at the tag
  (synced by the same PR) and uses a relative-path source for that agent.

## Testing

### Gradle plugin (`stlInfo --json`)

Functional tests, on the usual matrix, with the configuration cache:

| Case | Expectation |
|---|---|
| `stlInfo --json` on a STANDARD project | One line; parses; every schema key present with the profile's values |
| Explicit settings and `-Psterling.profile=lenient` | Reflected in the JSON; `profileProperty` is `lenient` |
| Two-module build, `-q stlInfo --json` | Two lines, `project` `:a` and `:b` |
| No `java` plugin | `javaToolchain.active` false with the reason; no `reports` |
| Error Prone off | `nullAway.enabled` true, `active` false |
| Threshold of 120 | Task FAILED with the validation message, as in text mode |
| Second run | Configuration cache reused |

Unit tests: the JSON writer's escaping; a golden schema file
(`src/test/resources/stl-info-v1.json`) compared to the keys the task emits,
so a removed or renamed key fails until `schemaVersion` is bumped.

### Repository checks (unit tests, part of `build`)

`AgentPluginTest`, beside `TaglineTest` and `DocsTest`:

- all three manifests have `name` `sterling` and `version` equal to
  `version.txt`; root `plugin.json` uses only Agent Plugins 1.0 fields and has
  no `extensions.com.openai`
- each `SKILL.md`: `name` equals its directory and follows the spec's rules,
  `description` 1–1024 chars, `metadata.sterling-version` equals `version.txt`
- every `stl*` task and every `sterling { }` setting a skill mentions exists
  (read from `SterlingPlugin`'s task names and the extension's getters, as
  `DocsTest` does)
- copies of shared scripts are byte-identical
- `allowed-tools` is a space-separated string whose rules all name a script
  under `${CLAUDE_SKILL_DIR}/scripts/` that exists in that skill
- `outcomes.sh` rejects `-I`, `--init-script`, `-D` and tasks outside its list
  (script test)
- `reference/duplicate-config.md` and `docs/guide/adopting.md` list the same
  tool settings
- the plugin description matches `pluginDescription`'s wording where it
  quotes it

### Scripts

JUnit tests in `functionalTest` run the Bash scripts against throwaway projects
in a `@TempDir` (Sterling's own wrapper copied in, so Gradle 9.9.1 runs):

- `detect.sh`: a Gradle 8.14 wrapper and a daemon JVM of 17 each stop in the
  static phase with the expected message (no Gradle run); a toolchain of 17
  stops after the Gradle phase; a project with Spotless, `com.github.spotbugs`,
  JaCoCo rules, Lombok and a generated source directory reports each; a
  two-module `build-logic` build reports the convention plugin
- `outcomes.sh`: a failing and a passing task report `FAILED` and `SUCCESS`;
  works with `--configuration-cache` on Gradle 9.0.0
- `coverage.sh`: a JaCoCo XML with known counters gives the expected floors,
  and `branch = 0` with no branches
- `shellcheck` on every script (CI)

### Skills (evals)

Skills are tested by running a real agent on a fixture and checking the
result with a script; the agent's prose is never the evidence.

**Fixtures** (`agent-plugins/evals/fixtures/`, each a self-contained Gradle
build committed as plain files; `run.sh` copies one to a temp dir, runs `git
init` and commits it):

| Fixture | Contents |
|---|---|
| `brownfield-service` | Spring Boot 4, Java 25, Gradle 9.x; Lombok and MapStruct; OpenAPI-generated sources; Spotless with `googleJavaFormat()` 1.x and `removeUnusedImports()`; full `com.github.spotbugs` at `MAX`; a JaCoCo rule at 50%; Spring's NullAway snippet; unformatted files; tests giving about 65% coverage |
| `brownfield-multi` | Two modules with different coverage, a `build-logic` convention plugin, a version catalog, prek config |
| `brownfield-aosp` | 4-space indented sources, Husky installed |
| `greenfield` | Spring Initializr output for Boot 4 and Java 25 |
| `unsupported` | One Gradle 8.14 build and one toolchain-17 build |
| `findings` | Sterling at `STANDARD` with one Error Prone error, a NullAway finding in a `@NullMarked` package, a SpotBugs finding and coverage under threshold |
| `previous-sterling` | `brownfield-service` already on the previous Sterling release |

**What "pass" means**, checked by `agent-plugins/evals/verify/<skill>.sh`:

| Skill | Pass |
|---|---|
| `adopt-sterling` (existing) | Commits in the specified order and nothing else; the reformat commit equals its parent plus `stlFmt`; its SHA in `.git-blame-ignore-revs`; no commit touches `src/**` except the reformat; `com.github.spotbugs`, the duplicate Spotless steps and the consumer NullAway config are gone; `stlInfo --json` thresholds equal each module's measured floors, set in each module's build script; profile is `STANDARD` if and only if `stlCheck -Psterling.profile=standard` succeeds; `.pre-commit-config.yaml` (or the Husky/Lefthook config) runs `stlFmtCheck`; `format.style` is what the run's prompt chose; no agent plugin settings written to the project; working tree clean |
| `adopt-sterling` (new) | Sterling at the skill's version on `STANDARD`; `stlFmtCheck` succeeds; hook present |
| `adopt-sterling` (unsupported) | No commits, no file changes; the report names the Gradle or JDK version to upgrade to |
| `fix-sterling-findings` | `stlCheck` SUCCESS; existing tests unchanged and passing; thresholds, `disable(...)`, excludes and filters unchanged; every new `@SuppressWarnings` or `<Match>` has a reason comment and appears in the report |
| `upgrade-sterling` | Version equals the skill's; a separate reformat commit if and only if `stlFmtCheck` fails after the bump (a new formatter version, or a changed default such as 0.3.0's `formatAnnotations`); `stlCheck` outcome reported matches a re-run |
| `sterling-conventions` | Asked to add a small feature (a REST endpoint with a service and a nullable query parameter) to `greenfield` with Sterling at `STRICT`: `stlCheck` succeeds on the first run after the agent's edit |

Plus a human check of each report against guardrail 8.

**Runs before each release** (`run.sh <agent> <skill> <fixture>` drives the
agent headless with the plugin loaded from `agent-plugins/sterling` and a
prompt that pre-approves the plan):

- `adopt-sterling` on `brownfield-service`: Claude Code three times, Copilot
  CLI once, Codex CLI once. Every run must pass; a flaky run is a failure to
  investigate, not a retry.
- Every other skill/fixture pair: Claude Code once and one other agent once.

**Dogfood:** `../blade-svc-java-webmvc` (Spring Boot 4, Java 25, Lombok).
`adopt-sterling` runs on a scratch clone checked out at its last commit before
Sterling, and passes the `adopt-sterling` (existing) checks. `upgrade-sterling`
runs on its current state, moving it from the previous release. Don't edit the service to
make a run pass; record what failed.

## CI and release

### On every PR (`ci.yml`, new `agent-plugin` job)

| Check | Notes |
|---|---|
| `claude plugin validate agent-plugins/sterling --strict` | Claude Code CLI installed from npm at a pinned version; no login needed. Fails on warnings too |
| Root `plugin.json` against the Agent Plugins 1.0 JSON Schema | Schema vendored under `agent-plugins/schemas/`, checked with `check-jsonschema` at a pinned version, so CI doesn't depend on agent-plugins.org being up |
| `shellcheck` on `agent-plugins/**/*.sh` | Preinstalled on Ubuntu runners |
| `AgentPluginTest`, the script tests and the `stlInfo --json` tests | Already in the `build` and `functionalTest` jobs |

Copilot, VS Code and Codex have no validate command. Their coverage is the
schema check, `AgentPluginTest` (which also enforces the Agent Skills naming
rules), and the manual installs below.

### Manual, before merging a release PR that changes the plugin

1. Install from the working tree in each agent: `claude --plugin-dir
   agent-plugins/sterling`, `copilot plugin install ./agent-plugins/sterling`,
   VS Code's *Chat: Install Plugin from Source* with the local folder at
   **User** scope, and a local Codex marketplace pointing at the directory.
   Check the four skills are listed.
2. The eval runs and the dogfood above.
3. `docs/guide/upgrading.md` has the release's section.

### Release

- `release-please-config.json` `extra-files` gains:

  ```json
  { "type": "json", "path": "agent-plugins/sterling/plugin.json", "jsonpath": "$.version" },
  { "type": "json", "path": "agent-plugins/sterling/.claude-plugin/plugin.json", "jsonpath": "$.version" },
  { "type": "json", "path": "agent-plugins/sterling/.codex-plugin/plugin.json", "jsonpath": "$.version" },
  "agent-plugins/sterling/skills/adopt-sterling/SKILL.md",
  "agent-plugins/sterling/skills/fix-sterling-findings/SKILL.md",
  "agent-plugins/sterling/skills/upgrade-sterling/SKILL.md",
  "agent-plugins/sterling/skills/sterling-conventions/SKILL.md"
  ```

  The `SKILL.md` files use the generic updater on the
  `# x-release-please-version` line.
- Commits touching only the plugin use `feat(skills):` or `fix(skills):`, and
  release with Sterling.
- **Marketplace PR.** A `pin-marketplace` job in `release.yml`, `needs:
  publish` (so the pinned Sterling version resolves from the Plugin Portal
  before any skill applies it):
  1. `actions/create-github-app-token` (pinned by SHA) for a GitHub App
     installed on `balakmran/agent-plugins` only, with `contents: write` and
     `pull-requests: write`. Its client ID is the `AGENT_PLUGINS_APP_CLIENT_ID` variable
     and its key the `AGENT_PLUGINS_APP_KEY` secret, both in a `release`
     environment.
  2. Check out `balakmran/agent-plugins` with that token; run
     `scripts/pin.sh sterling <tag> <sha>`, where the SHA is
     `git rev-parse <tag>^{commit}` from this repo's checkout.
  3. Push `pin/sterling-<tag>` and `gh pr create` with the changelog link.
  - An App, not `GITHUB_TOKEN` (can't write another repo) and not a PAT
    (expires, tied to a person). A PR opened with the App's token also starts
    the marketplace repo's CI, which a `GITHUB_TOKEN` PR wouldn't.
- **After release (manual):** merge the marketplace PR once its CI passes;
  then in each agent (VS Code included), refresh the marketplace and update
  or install `sterling@balakmran`, and check the installed version.

### README

A "Using Sterling with a coding agent" section, after installation. Every
command installs for the current user only:

```bash
# Claude Code
claude plugin marketplace add balakmran/agent-plugins --scope user
claude plugin install sterling@balakmran --scope user

# GitHub Copilot CLI (VS Code picks up plugins installed here)
copilot plugin marketplace add balakmran/agent-plugins
copilot plugin install sterling@balakmran

# Codex CLI
codex plugin marketplace add balakmran/agent-plugins
codex plugin add sterling@balakmran
```

VS Code: Agents window > Customizations > Plugins > Configure Marketplaces >
Add Marketplace, `balakmran/agent-plugins`, then install Sterling under
**User** (or use the `vscode://chat-plugin/add-marketplace` link in the
README). Plus: what each skill does and how to invoke it
(`/sterling:adopt-sterling` in Claude Code), that the skills apply the
Sterling version they were released with, how to update in each agent
(Claude's auto-update is off for third-party marketplaces), and that Codex's
IDE extension doesn't support plugins.

## Work breakdown

One PR per step, in order; each leaves `./gradlew build functionalTestLocal`
green.

1. **`feat: stlInfo --json`**: task option, JSON writer, report paths, tests,
   schema docs in `docs/guide/configuration.md`, golden schema test. Dogfood
   `stlInfo --json` on the service.
2. **`docs: upgrade notes`**: `docs/guide/upgrading.md` with a 0.1.0 → 0.2.0
   section kept current through 0.2.0, and its `DocsTest` check.
3. **`build: agent plugin scaffold`**: `agent-plugins/sterling/` with the
   three manifests and `sterling-conventions` as the first skill;
   `AgentPluginTest`; the vendored schema; the `agent-plugin` CI job;
   release-please `extra-files`. Re-check every *(summary)* fact above.
   Update `CLAUDE.md`'s Layout section.
4. **`feat(skills): detection and outcome scripts`**: `detect.sh`, the init
   scripts, `outcomes.sh`, `coverage.sh`, with their tests.
5. **`feat(skills): adopt-sterling`**: the skill, `reference/`, the
   `brownfield-service`, `brownfield-multi`, `greenfield` and `unsupported`
   fixtures, `run.sh` and `verify/adopt-sterling.sh`.
6. **`feat(skills): fix-sterling-findings`**: skill, `findings.sh`, the
   `findings` fixture and verify script.
7. **`feat(skills): upgrade-sterling`**: skill, `previous-sterling` fixture,
   verify script.
8. **`feat(skills): sterling-conventions rules`**: the Error Prone and
   SpotBugs lists checked against the pinned versions; its eval.
9. **`ci: pin the marketplace on release`**: the `pin-marketplace` job.
   Manual, outside the repo: create the GitHub App, install it on
   `balakmran/agent-plugins`, add the variable, secret and `release`
   environment; in the marketplace repo, add the two marketplace files and `pin.sh`;
   add the `agent-skills` topic to `balakmran/sterling`.
10. **`docs: install the agent plugin`**: README section, `ROADMAP.md` entry
    under 0.2.0 and the decisions log.
11. **Release**: evals and dogfood, then the release PR, the marketplace PR,
    and the post-release installs.

## Done when

- [x] `stlInfo --json` ships, with tests on the full matrix and the schema
      documented
- [x] `./gradlew build functionalTestLocal` is green, and CI's
      `agent-plugin` job passes `claude plugin validate --strict`, the schema
      check and `shellcheck`
- [ ] All four skills pass their evals as defined in Testing, in Claude Code,
      Copilot CLI and Codex CLI, and use every v0.2.0 setting as tabled
- [x] `adopt-sterling` and `upgrade-sterling` pass on `blade-svc-java-webmvc`
- [x] release-please bumped all three manifests and every
      `sterling-version` in the release PR
- [x] The release opened a marketplace PR that pins the tag and SHA, merged
      after its CI passed
- [ ] `sterling@balakmran` installs at user scope from the marketplace in
      Claude Code, Copilot CLI, VS Code and Codex, at the released version
- [x] The README documents installing and updating for each agent

## Open questions

None left for planning. Two things are checked during implementation rather
than decided now:

- When the marketplace repo is set up: which of its two files VS Code reads.
  Either works, since each uses only source types its own format defines.
- During the manual installs: whether VS Code honours `allowed-tools` (not
  documented). Nothing depends on it.

### Settled (2026-11-14)

- **VS Code** reads Copilot, Claude and legacy plugin formats; users add the
  `balakmran/agent-plugins` marketplace and install at **User** scope, or
  install with Copilot CLI, which VS Code discovers. Install-from-source
  can't reach the subdirectory.
- **Codex**, from its source: reads `.claude-plugin/marketplace.json` when no
  `.agents/plugins/marketplace.json` exists; `git-subdir` takes `ref` and
  `sha` and checks out `sha`; installs with `codex plugin add
  sterling@balakmran` into `~/.codex/config.toml`. So the marketplace repo
  has two files, not three.
- **`.codex-plugin/plugin.json`** stays: the root manifest is canonical for
  identity and skills, and the Codex file carries `interface` because the
  root has no `extensions.com.openai`.
- **Task outcomes:** a `Plugin<Settings>` applied from the init script,
  injected with `BuildEventsListenerRegistry`; prototype passed on Gradle
  9.0.0 and 9.9.1 with the configuration cache.
- **`allowed-tools`:** Claude Code syntax, the skill's own scripts only, with
  `outcomes.sh` accepting a fixed argument set; Copilot prompts, Codex
  ignores it.
- No `gh skill`, `npx skills` or `npx plugins`: vendor plugin managers only,
  user scope only.
- `claude plugin validate` needs no login; it runs in CI.
- Pre-commit hook: committed `.pre-commit-config.yaml` (or the project's
  Husky/Lefthook config); the user runs `prek install`.
- Every v0.2.0 feature is used by the skills; `format.style` defaults to
  `GOOGLE`.
- Coverage thresholds are per module, in each module's build script.
- No automated eval runs.
- The `agent-skills` topic is added to `balakmran/sterling`.
