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
revisions from the reference workflow, installs JDK 21/25, runs verifyLocalFull and retains logs/JARs as
CI artifacts with read-only repository permissions. It does not publish releases. It has not been run
on GitHub during local bootstrap. Each CI job already has an isolated checkout/home, so it uses the
ordinary Gradle wrapper. Reflink-specific tooling tests explicitly skip on unsupported runner filesystems;
local supported-filesystem verification remains necessary to claim copy-on-write workflow coverage.

Kotlin formatting/lint uses the same pinned kotlinter plugin as Compukters. Run :core:formatKotlin or
:v1_21_1-neoforge:formatKotlin through the managed wrapper; check tasks include lint. Edit and format the
canonical shared roots, never generated mirrors. Generated shared mirrors are excluded from lint tasks.
