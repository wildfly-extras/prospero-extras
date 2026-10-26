# Project Coding Rules (Non-Obvious Only)

- Static utility methods on command classes are mandatory for testability: tests call e.g. `ManifestMergeCommand.merge(...)` directly, not through the CLI. When adding a new command, expose its core logic as a `public static` method.
- All commands must return one of the constants in [`ReturnCodes`](../../src/main/java/org/wildfly/prospero/extras/ReturnCodes.java) (`SUCCESS=0`, `INVALID_ARGUMENTS=1`, `ERROR=2`).
- Group commands (those with subcommands) must extend [`AbstractParentCommand`](../../src/main/java/org/wildfly/prospero/extras/shared/AbstractParentCommand.java) and register subcommands via `addSubCommand()` in [`Main`](../../src/main/java/org/wildfly/prospero/extras/Main.java), not via `@Command(subcommands=...)` annotation.
- Checkstyle (wildfly-checkstyle) runs at the `compile` phase and **breaks the build on violations** — run `mvn checkstyle:checkstyle` before committing to catch issues early.
- Apache 2.0 license header is required on every new source file; copy from any existing file in `src/main/java/org/wildfly/prospero/extras/`.
- Use `org.jboss.logging.Logger` for logging — not SLF4J or `java.util.logging`.
- There is a typo package `repoository` (double 'o') alongside `repository`. All **new** repository-related code goes in `repository` only.
- The public programmatic API is [`ProsperoExtras.manifestOperations()`](../../src/main/java/org/wildfly/prospero/extras/ProsperoExtras.java) — if a new manifest operation is added to the CLI it should also be exposed via [`ManifestOperations`](../../src/main/java/org/wildfly/prospero/extras/manifest/ManifestOperations.java) and [`ManifestOperationsImpl`](../../src/main/java/org/wildfly/prospero/extras/manifest/ManifestOperationsImpl.java).
