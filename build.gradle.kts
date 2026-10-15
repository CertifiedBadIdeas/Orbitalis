// SPDX-License-Identifier: Apache-2.0

plugins { base }
group = providers.gradleProperty("modGroup").get()
version = providers.gradleProperty("modVersion").get()

val verifyTooling = tasks.register<Exec>("verifyTooling") {
    group = "verification"
    commandLine("python3", "tools/verify.py", "tooling")
}
val verifyArchitecture = tasks.register<Exec>("verifyArchitecture") {
    group = "verification"
    commandLine("python3", "tools/verify.py", "architecture")
}
tasks.register("verifyLocalFast") {
    group = "verification"
    description = "Tooling, architecture boundaries and independent core checks; no game-runtime claim."
    dependsOn(verifyTooling, verifyArchitecture, ":core:check")
}
tasks.register("verifyLocalFull") {
    group = "verification"
    description = "All current module checks and the verified production archive; no visual/GameTest claim."
    dependsOn("verifyLocalFast", ":v1_21_1-common:check", ":v1_21_1-neoforge:check", ":v1_21_1-neoforge:verifyProductionJar")
}
tasks.named("check") { dependsOn("verifyLocalFull") }
tasks.named("assemble") { dependsOn(":v1_21_1-neoforge:buildProductionJar") }
