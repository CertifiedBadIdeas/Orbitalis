<!--
The Orbitalis Developers

Copyright 2026 The Compukters Developers
Copyright 2026 Vsevolod Petrov (lazyhat)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Development and collaboration

## Prerequisites and bootstrap

Use JDK 25 to launch Gradle 9.8.1 and install JDK 21 for project compilation. Set JAVA_HOME or configure
Gradle's installation discovery. Python 3.11+, Git, GNU cp, flock and a filesystem supporting reflinks
are needed by the Linux development helpers. The Gradle wrapper is checksummed. The helpers are Linux
workflows; gradlew.bat is available for ordinary Windows Gradle use, without the Linux isolation helpers.

Run ./worktree-task doctor. In an unborn repository it reports that the first commit is required for
worktrees, while still checking JDK/cache prerequisites. Do not manufacture an initial commit that absorbs
someone else's staged files. During this first bootstrap, work in the original checkout and preserve its index.

## Isolated tasks

After the first commit, run ./worktree-task feat/example from the main checkout. Default bases are the
configured local orbitalis.baseBranch, then dev/main/master. --base selects an explicit existing local
branch. No implicit fetch, branch rename or remote setup occurs. --path can choose a destination;
--resume completes interrupted setup only in the same clean registered task checkout. Dirty interrupted
checkouts are preserved; complete setup manually without resetting them.

Required Git submodules, if added later, are opt-in through repeatable --submodule or --all-submodules.
There is no implicit Compukters VM. --repo selects an initialized nested repository without changing its
parent pin. Use ./worktree-task doctor --path <checkout> before building.

For noninteractive Gradle use ./gradlew-sandbox-dev-parallel-summary <tasks>. The log is stored in
build/agent-logs and failures return Gradle's actual status with a diagnostic tail. For runClient or
runServer use ./gradlew-sandbox-dev-parallel. Managed builds across worktrees share one workspace slot;
each build uses up to eight workers, configurable via ORBITALIS_GRADLE_WORKERS or --max-workers.
A live managed client holds that slot, so stop it before verification. Dedicated server EULA acceptance
is manual. Run directories are isolated by checkout and target. Never open a user's existing world for a preview.

## Gradle caches

Each checkout owns .gradle-sandbox. Managed builds also use .gradle-agent for the project cache,
separate from an IDE sync using .gradle, so their file-hash locks do not collide. The helper atomically publishes a stable workspace template under
.toolchain/gradle-cache/template and clones with cp --reflink=always, preserving independent inodes,
mutations and locks. First use can bootstrap from idle workspace caches or ~/.gradle; otherwise it starts
cold and populates on a successful online build. Cold/populated is not proof of offline completeness.

Use ./gradlew-sandbox-cache seed <idle-home> to seed explicitly. Do not use a source that an unmanaged
Gradle process may mutate. ORBITALIS_GRADLE_CACHE_TEMPLATE overrides the template path;
ORBITALIS_GRADLE_CACHE_SOURCES is an os.pathsep-separated source list (empty disables automatic sources).
No hardlinks, symlinks to writable homes, manual copies or full-copy fallback. Reflink capability is a
prerequisite. Template refresh failure after a successful build is reported separately and does not
change Gradle's status. See the cache tests for concurrent publication and interrupted initialization.

## Multi-agent work

Delegate only when requested for the current task. Assign every implementation agent its own task branch
and worktree, a bounded feature/area, expected interface, dependencies and verification. The coordinator
owns shared Gradle configuration and cross-module contracts; settle those before parallel dependent edits.
Do not share worktrees, writable caches or game directories. Concurrent lightweight investigation is fine;
heavy Gradle builds are serialized by the shared slot. Agents report actual revision, changed paths, checks
and remaining evidence. One coordinator reviews and performs authorized integration, then checks the
combined result. Preserve other agents' branches, worktrees, processes and uncommitted changes.

## Completion

Use narrow checks while iterating; inspect git diff and effective author/committer before a scoped commit.
Do not include unrelated staged files. There is no inherited automatic merge authorization from Compukters.
After authorized integration and verification, run ./worktree-task finish <branch> from outside that task
checkout. It verifies integration into the recorded base and cleanliness, archives build/agent-logs plus
ignored user files, and removes only that checkout. --evidence supplies additional evidence. It keeps the
branch, does not merge or run checks, and refuses unsafe completion. Do not force-remove a refused checkout.

Keep temporary plans/specs in ignored .agents/tmp, and maintained contributor documentation in docs.
GitHub remote, Roadmap project, publishing targets and credentials are not configured by this bootstrap.

## Continuous integration

.github/workflows/verify.yml is ready for this repository's future GitHub remote. It uses pinned action
revisions from the reference workflow, installs JDK 21/25, runs verifyLocalFull and the headless laboratory
GameTests, and retains logs/JARs/GameTest XML as
CI artifacts with read-only repository permissions. It does not publish releases. It has not been run
on GitHub during local bootstrap. Each CI job already has an isolated checkout/home, so it uses the
ordinary Gradle wrapper. Reflink-specific tooling tests explicitly skip on unsupported runner filesystems;
local supported-filesystem verification remains necessary to claim copy-on-write workflow coverage.

Kotlin formatting/lint uses the same pinned kotlinter plugin as Compukters. Run :core:formatKotlin or
:v1_21_1-neoforge:formatKotlin through the managed wrapper; check tasks include lint. Edit and format the
canonical shared roots, never generated mirrors. Generated shared mirrors are excluded from lint tasks.

## Agent skills

The only skills versioned in `.agents/skills/` are the six `orbitalis-*` workflows. They cover
project-specific Minecraft integration, contracts, UI, visual assets, release evidence and cross-layer
triage. Their source of truth belongs to this repository.

General development workflows live in [ai-skills](https://github.com/lazyhat/ai-skills). They include
`production-workflow`, `brainstorming`, `debugging-strategy`, `testing-strategy`, `writing-plans`,
`executing-plans`, `verification-workflow`, `review-feedback` and `using-github-roadmap`.
Use the upstream [installation instructions](https://github.com/lazyhat/ai-skills#usage); keep one
user-scoped installation rather than copied per-project versions. Installation is optional and is
not performed by Orbitalis setup, Gradle or CI.

For a fresh Codex user-scoped setup, choose an external checkout location, for example:

```sh
mkdir -p "$HOME/src" "$HOME/.agents"
git clone https://github.com/lazyhat/ai-skills.git "$HOME/src/ai-skills"
if [ ! -e "$HOME/.agents/skills" ] && [ ! -L "$HOME/.agents/skills" ]; then
    ln -s "$HOME/src/ai-skills/skills" "$HOME/.agents/skills"
else
    printf 'Existing skills installation found; inspect it before changing it.\n'
fi
readlink -f "$HOME/.agents/skills"
```

Reuse an existing ai-skills checkout when available; do not rerun clone into an occupied location.
The resolved link should point to that checkout's `skills/` directory. If a user-scoped skills directory
already contains other skills, preserve it and follow the upstream instructions to arrange discovery;
do not overwrite it or create a nested `skills/skills` link. Check the resolved path before relying on
shared skills. Follow the upstream instructions for updates and other agent discovery mechanisms.

Contributors without those skills use [AGENTS.md](../../AGENTS.md) and these contributor guides directly.
All mandatory Orbitalis rules, commands, ownership boundaries, verification and licensing remain in
this repository. Optional skills do not override them or introduce Compukters' merge authorization,
VM/submodule requirements or GitHub project configuration. Builds and tests do not load external skills.
