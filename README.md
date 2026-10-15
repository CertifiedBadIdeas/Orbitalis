# Orbitalis

Космический мод для Minecraft. Текущая цель — **1.21.1 / NeoForge**, язык — Kotlin.
Это рабочий каркас разработки; игровых механик пока нет. Поддержка 26.1 ещё не реализована.

Процесс разработки и разделение модулей перенесены и адаптированы из Compukters.

## Начало работы

Нужны JDK 25 для Gradle, JDK 21 для целевого кода, Python 3.11+, Git, GNU cp с reflink и flock.

```sh
export JAVA_HOME=/path/to/jdk-25
./worktree-task doctor
./gradlew-sandbox-dev-parallel-summary verifyLocalFast
./gradlew-sandbox-dev-parallel-summary verifyLocalFull
./gradlew-sandbox-dev-parallel :v1_21_1-neoforge:runClient
```

Для первого запуска dedicated dev server прочитайте и примите Minecraft EULA в его отдельном
run/server/eula.txt; скрипты не принимают её за вас.

После первого коммита создавайте изолированные задачи:

```sh
./worktree-task feat/first-space-feature
```

Основные документы: [разработка](docs/contributors/development.md),
[архитектура и перенос на 26.1](docs/contributors/architecture.md),
[проверки](docs/contributors/verification.md), [происхождение переноса](docs/contributors/bootstrap-provenance.md).
Правила агентов — [AGENTS.md](AGENTS.md), лицензии — [LICENSE.md](LICENSE.md), [NOTICE](NOTICE)
и [учёт сторонних материалов](licenses/README.md).
