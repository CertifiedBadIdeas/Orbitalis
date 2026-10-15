# Orbitalis repository guidelines

## Checkout and collaboration

Read docs/contributors/development.md and architecture.md before architectural changes.
Use ./worktree-task <task-branch> after the first commit. It selects orbitalis.baseBranch
(Git config), otherwise the first existing local branch among dev, main, master. Use --base
to override. Do not fetch implicitly. Keep edits and checks in the task checkout; reuse it only
for the same task. Explicit user checkout instructions take precedence.
The initial bootstrap may run in the original checkout while HEAD is unborn. Preserve the
user's existing index and IDE files; do not include them in an agent commit without agreement.
Do not automatically rename branches, merge, push, tag, publish, or open PRs. Compukters'
standing integration authorization is not inherited by this separate repository.
Verify effective Git author and committer before committing; never invent an agent identity.
Use focused Conventional Commits and rewrite only unpublished commits belonging to the task.

Do not spawn agents unless the user explicitly requests delegation or parallel agent involvement
for the task. A request for a multi-agent-capable environment alone is not a request to spawn.
For authorized delegation, assign independent worktrees, bounded ownership, interfaces and checks.
Agents must not share a writable Gradle home or edit the same files concurrently. The coordinator
owns common build configuration, integration and final verification. Read the collaboration guide.

## Build and verification

Run Gradle on JDK 25; common and 1.21.1 bytecode targets Java 21. Kotlin is pinned in
 gradle/libs.versions.toml. Use ./worktree-task doctor before builds.
For noninteractive tasks use ./gradlew-sandbox-dev-parallel-summary <tasks>; full output is
retained in build/agent-logs. For interactive runClient/runServer use
./gradlew-sandbox-dev-parallel. A live client holds the workspace slot; stop it before building.
The wrappers serialize heavy builds across worktrees, with eight workers by default;
ORBITALIS_GRADLE_WORKERS or --max-workers overrides the limit.
Use only the cache helper for independent reflink homes; no hardlinks, manual cache copies or
writable-home symlinks. Do not seed an unmanaged active cache.

Select proportional evidence using docs/contributors/verification.md. verifyLocalFast covers tooling,
source boundaries and core checks. verifyLocalFull adds all current Minecraft module checks and the
production JAR; it does not run a game server or prove visual correctness. Do not substitute build
success for an observed gameplay scenario. Run real GameTests once introduced for world behavior.
A source ownership scan is a guardrail, not a complete semantic dependency proof.
Before completion run fresh relevant checks and inspect the diff. After a coordinated bootstrap or
build architecture change, run verifyLocalFull. Do not require it for each localized iteration.
Use worktree-task finish only after authorized integration and verification; it archives evidence
and ignored user files and removes only a clean integrated recorded checkout, retaining its branch.
Never bypass a refusal with force-removal or remove another agent's checkout.

## Architecture

modules/common/core has no Minecraft, NeoForge or client dependencies. Put simulation/domain logic
there as features arrive; no speculative game systems are required by this scaffold.
modules/minecraft/shared/common owns canonical Minecraft behavior, independent of NeoForge.
modules/minecraft/shared/neoforge owns canonical loader registration and adapters.
modules/minecraft/v1_21_1 contains compatibility and packaging leaves. Future v26_1 leaves must
compile the same canonical shared sources with version differences behind explicit compatibility
interfaces. Never import a version implementation into shared code or emit net.minecraft classes.
Never put Java 25 bytecode in the 1.21.1 distribution. See the migration checklist in architecture.md.
Kotlin packages start with org.cbi.orbitalis; four-space indentation and *Test.kt test names.
Use the pinned kotlinter formatKotlin/lintKotlin tasks; generated mirrors are excluded.

## Local skills and working documents

Read relevant .agents/skills/<name>/SKILL.md when working in its area:
orbitalis-neoforge-integration, orbitalis-contract-change, orbitalis-ui-development,
orbitalis-visual-assets, orbitalis-release, orbitalis-cross-layer-triage.
Only Orbitalis-specific skills are stored here. Shared development skills come from
https://github.com/lazyhat/ai-skills and are optional contributor tooling; see the Agent skills section
in docs/contributors/development.md for setup. Do not vendor, fork or require them to build or contribute.
Repository and user instructions override generic skill defaults, including Compukters-specific rules
in an installed shared skill. This AGENTS.md and the contributor guides remain the repository policy
whether or not the shared skills are installed.

For failures, reproduce the problem and identify its owning boundary before fixing it. Resolve material
architecture/scope decisions before dependent implementation, and keep coordinated plans in the ignored
working-document directories below. Check actual results before claiming success; report missing evidence.
For review feedback, verify the claim against current code and repository decisions before changing code.
Keep temporary specs/plans under ignored .agents/tmp/specs and .agents/tmp/plans. Durable architecture
and contributor documentation belongs in docs/contributors. Use English for repository documentation.
GitHub is not configured yet: do not create issues in Compukters or reuse its Projects IDs. Once this
repository has its own roadmap, configure .github/copilot-instructions.md and link architecture-scale
work to an issue on that roadmap; apply using-github-roadmap when installed. Until then record durable
decisions locally and continue development.
Use gh for authorized GitHub operations; remote operations require network permission.

## Licenses and releases

The project uses Apache-2.0; preserve LICENSE.md, NOTICE and upstream attribution in adapted code.
Use the full Apache-2.0 source header in original Kotlin, Gradle, Python, shell and executable/build
configuration files, following the format in build.gradle.kts and tools/verify.py. Use
`Copyright 2026 The Orbitalis Developers` for current original files; use the actual creation year for
future files. A bare SPDX identifier does not replace this header. Keep shebangs first in scripts.
Preserve upstream copyright and adaptation notices in imported code; never replace a third-party
holder with Orbitalis. Do not overwrite vendored/generated files with project headers.
Follow licenses/README.md for every dependency or asset added.
Never copy Compukters branding, asset rights, unrelated native runtimes or release credentials.
Use the local release skill before distributable/release claims. Actual production archives must pass
verifyProductionJar including identity, compatibility, Java 21 bytecode, nested Kotlin and legal files.
Keep user/contributor-visible changes in docs/CHANGELOG.md under the current development version.
A new dev-cycle correction is not a published-release regression. No publish/upload/tag authorization
is implied by a successful build or release check.
