/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Shared by the canonical and consumer harnesses. Interactive saves never use this directory.
val runId = java.util.UUID.randomUUID().toString()
val runsRoot = layout.buildDirectory.dir("agent-logs/gametest-runs").get().asFile
val runDirectory = runsRoot.resolve(runId)
extra["orbitalisGameTestRunDirectory"] = runDirectory.absolutePath
val helper = file(if (file("../../tools/gametest.py").isFile) "../../tools/gametest.py" else "${rootProject.projectDir}/tools/gametest.py")
val nativeRun = tasks.matching { it.name == "runGameTestServer" }
val finishRun = tasks.register("finishGameTestRun") {
    doLast {
        val run = tasks.named("runGameTestServer").get()
        val args = mutableListOf("python3", helper.absolutePath, "finish", runsRoot.absolutePath, runId,
            "--report", layout.buildDirectory.file("test-results/gameTestServer/results.xml").get().asFile.absolutePath)
        if (run.state.executed && !run.state.skipped && run.state.failure == null) args += "--success"
        val execution = providers.exec { commandLine(args) }
        execution.result.get().assertNormalExitValue()
        logger.lifecycle(execution.standardOutput.asText.get().trim())
    }
}
val prepareRun = tasks.register("prepareGameTestRun") {
    doLast {
        providers.exec {
            commandLine("python3", helper.absolutePath, "prepare", runsRoot.absolutePath, runId)
        }.result.get().assertNormalExitValue()
    }
}
nativeRun.configureEach {
    dependsOn(prepareRun)
    finalizedBy(finishRun)
}
