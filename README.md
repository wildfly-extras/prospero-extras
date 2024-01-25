## Prospero-extras

A collection of command line operations to assist in working with [wildfly-channels](https://github.com/wildfly-extras/wildfly-channel).

## Using prospero-extras

The latest available release is available on [github](https://github.com/spyrkob/prospero-extras/releases/latest). The tool is distributed as an executable jar.

You can use `-h` argument to displays available commands, e.g.:

```
java -jar prospero-extras-1.0.0.Beta1-shaded.jar -h
Usage: tools [-h] [COMMAND]

Options:
  -h, --help
Commands:
  create-bundle
  manifest-diff
  download-diff
  manifest-merge  Merges streams from two manifests.
  from-channel
  from-list
  channel
  repository
```

The same `-h` argument can be used on each command to displays additional usage information, e.g.:

```
java -jar prospero-extras-1.0.0.Beta1-shaded.jar manifest-merge -h
Merges streams from two manifests.
Usage: tools manifest-merge [-h] [--mode=<mergeStrategy>] <manifestOne> <manifestTwo>
Prints a manifest containing streams from both input manifests. If the samestream is available in both input manifests, the conflict is resolved using a merge strategy.
The LATEST merge strategy compares the versions and picks the latest stream.
The FIRST merge strategy chooses the stream from `<manifestOne>`.

Positional parameters:
      <manifestOne>
      <manifestTwo>

Options:
  -h, --help
      --mode=<mergeStrategy>
                      merge strategy to use. The default strategy is LATEST.
```

## Building

Project requires JDK 11+ and Apache Maven 3.9.0+
 
`mvn clean install`
