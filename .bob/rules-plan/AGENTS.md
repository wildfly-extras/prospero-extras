# Project Architecture Rules (Non-Obvious Only)

- Command registration is **imperative** (done in [`Main.createCommandLine()`](../../src/main/java/org/wildfly/prospero/extras/Main.java)), not declarative (`@Command(subcommands=...)`). Adding a command requires editing `Main`.
- Group commands hold their `CommandLine` context via constructor injection; `AbstractParentCommand.getCtx()` looks up the subcommand tree by name — the `name` constructor argument must exactly match the picocli command name.
- The shaded jar (`*-shaded.jar`) is the distributable artifact; the plain jar does not include dependencies. Always reference the shaded jar when testing the CLI end-to-end.
- `VersionMergeStrategy` is both an interface and an enum (`Strategies`) in the same file. Enum values delegate to package-private strategy classes in the same file. New strategies must be added as a new enum constant plus a new package-private class.
- `ChannelOperations.ChannelManifestDownload` is a public inner class with public fields (not getters) — this is intentional and not a mistake.
- The `ManifestUtils.getLatestSchemaVersion()` helper is the only approved way to select a schema version when creating merged manifests; don't hardcode `ChannelManifestMapper.CURRENT_SCHEMA_VERSION`.
