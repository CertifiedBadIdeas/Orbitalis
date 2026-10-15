---
name: orbitalis-release
description: Build, inspect or claim readiness of an Orbitalis distributable JAR or release. Excludes ordinary development runs without an artifact claim.
---

# Verify the actual distribution

Read docs/contributors/verification.md and licenses/README.md.
For the current target run :v1_21_1-neoforge:buildProductionJar; verifyProductionJar inspects the remapped
archive, entry point, metadata/ranges, Java 21 project bytecode, legal notices and nested Kotlin runtime.
Do not distribute an intermediate dev or plain common JAR. Report exact artifact path, SHA-256,
revision and verification commands. A scaffold build has no evidence of gameplay or visual readiness.
Before a release claim run verifyLocalFull and relevant actual game/server scenarios on that candidate;
state any manual evidence that remains pending. Future targets need independent packaging verification.
Do not reuse Compukters native bundles, universal-release task names, upload scripts or credentials.
No release automation is configured here. Builds do not authorize tagging, pushing, uploading,
creating GitHub releases or publishing to distribution platforms.
