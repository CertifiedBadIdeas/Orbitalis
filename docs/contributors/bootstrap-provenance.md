<!--
The Orbitalis Developers

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

# Compukters workflow import

Source: local Compukters checkout, https://github.com/CertifiedBadIdeas/Compukters,
revision d2a6dccd11aae3c1ed801a0d40bf9542da61389c, inspected 2026-10-15.
Only tooling and process conventions were imported; the source checkout was not modified.

| Source practice | Orbitalis adaptation |
| --- | --- |
| gradlew-sandbox* and tools/gradle | Imported with tests; ORBITALIS environment namespace, no Rust job flags |
| worktree-task and tools/worktrees | Imported with tests; local base detection/config, opt-in submodules, unborn doctor |
| AGENTS.md and contributor guides | Adapted to current modules, authorized operations and honest evidence scope |
| Shared workflow skills | Use the upstream ai-skills user installation; no vendored copies or Orbitalis forks; repository-specific policy stays in AGENTS.md |
| NeoForge, ABI, triage, UI, visual and release skills | Rewritten for Orbitalis ownership and current capabilities |
| Canonical Minecraft shared roots | Shared common/neoforge layers plus version leaves and generated mirror support |
| Apache-2.0 and attribution | LICENSE.md, NOTICE, retained upstream tooling headers, full Apache-2.0 source headers and JAR legal checks |
| GitHub workflow conventions | Read-only verification workflow with pinned action revisions, JDK 21/25 and retained logs/JARs; no publishing |
| Pinned toolchains | Gradle checksum, Kotlin/Loom/NeoForge catalog, separate build JVM and target bytecode |
| Continuous changelog | One development section in docs/CHANGELOG.md |

Guest-language, native VM, FFM/JNI and compiler-specific workflows were not copied as active Orbitalis
skills because those systems do not exist here. Their general boundary-contract and ownership practices
are covered by orbitalis-contract-change and orbitalis-cross-layer-triage. The addon-only visual-preview
and VM/release upload scripts assume Compukters-specific layouts, credentials and artifacts; use the
owning Orbitalis client for preview and buildProductionJar for the current artifact. No dead wrappers
or VM-dependent verification tasks are presented as working commands.

Compukters' GitHub issue/project identifiers, automatic local merge authorization, branding and asset
licenses are not transferred. Configure this repository's own remote/roadmap when one exists.
