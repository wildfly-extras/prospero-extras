# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Stack

- **Language**: Java 17 (compiled with `source`/`target` 17; tested on JDK 17 and 21)
- **Build**: Maven (`mvn`), parent POM `org.jboss:jboss-parent:39`
- **CLI framework**: picocli 4.6.3
- **Testing**: JUnit Jupiter 5.10.1 + AssertJ 3.25.2
- **Checkstyle**: `wildfly-checkstyle-config` runs at the `compile` phase (failures block the build)
- **Artifact**: Packaged as a shaded ("fat") jar via `maven-shade-plugin`; main class is [`org.wildfly.prospero.extras.Main`](src/main/java/org/wildfly/prospero/extras/Main.java)

## Commands

```bash
# Build and test (also runs checkstyle)
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Run a single test class
mvn test -Dtest=ManifestMergeCommandTest

# Run a single test method
mvn test -Dtest=ManifestMergeCommandTest#pickFirstVersion

# Run checkstyle only
mvn checkstyle:checkstyle

# Run the shaded jar
java -jar target/prospero-extras-*-shaded.jar -h
```

## Architecture

All commands are `picocli` `@CommandLine.Command` classes. Each leaf command implements [`Command`](src/main/java/org/wildfly/prospero/extras/shared/Command.java) (`Callable<Integer>`). Group commands extend [`AbstractParentCommand`](src/main/java/org/wildfly/prospero/extras/shared/AbstractParentCommand.java), which prints usage and returns `ReturnCodes.INVALID_ARGUMENTS` when called without a subcommand. Help (`-h`/`--help`) is mixed-in via [`CommandWithHelp`](src/main/java/org/wildfly/prospero/extras/shared/CommandWithHelp.java).

Return codes live in [`ReturnCodes`](src/main/java/org/wildfly/prospero/extras/ReturnCodes.java): `SUCCESS=0`, `INVALID_ARGUMENTS=1`, `ERROR=2`.

Public API surface is exposed through [`ProsperoExtras.manifestOperations()`](src/main/java/org/wildfly/prospero/extras/ProsperoExtras.java) backed by [`ManifestOperations`](src/main/java/org/wildfly/prospero/extras/manifest/ManifestOperations.java).

**Note**: there are two nearly-identical packages — `repoository` (typo, legacy) and `repository` (canonical). New code goes in `repository`.

## Code Style

- Checkstyle config: `wildfly-checkstyle/checkstyle.xml` (from `wildfly-checkstyle-config` dependency). Violations fail the build at compile time.
- License header: Apache 2.0 block comment is required on all source files (see any file in `src/main/java/org/wildfly/prospero/extras/` with the full header).
- Logging: use `org.jboss.logging.Logger` (not SLF4J or java.util.logging). Root log level is ERROR; `org.wildfly.prospero` is INFO.
- Static utility methods on command classes are the preferred pattern for testability — tests call `ManifestMergeCommand.merge(...)` or `ManifestSubtractCommand.subtract(...)` directly without spawning the full CLI.
- Tests use package-private class visibility (no `public` on test classes; `public` on individual `@Test` methods).
- Import order follows Checkstyle rules; static imports are grouped at the top in test files.
