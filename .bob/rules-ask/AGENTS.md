# Project Documentation Rules (Non-Obvious Only)

- The `repoository` package (double 'o') is a **typo/legacy** package — it is not a separate concept. The canonical package is `repository`.
- Command classes double as both the CLI entry point and the library implementation — the static methods on them (e.g. `ManifestMergeCommand.merge`) are the authoritative implementation, not internal helpers.
- The [`ManifestOperations`](../../src/main/java/org/wildfly/prospero/extras/manifest/ManifestOperations.java) interface is the public Java API; the CLI commands are a separate consumer of the same logic.
- Checkstyle configuration is inherited from `org.wildfly.checkstyle:wildfly-checkstyle-config` as a Maven dependency — there is no local checkstyle XML file in the repo.
- Tests are in the same package as production code (package-private test classes) but physically under `src/test/java`.
