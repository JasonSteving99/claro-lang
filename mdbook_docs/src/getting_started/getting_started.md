# Create your own Claro Project with Bazel!
### 1 - Install Bazel (Required)
Simply install Bazel - instructions to install via Bazelisk can be found [here](https://bazel.build/install/bazelisk).

### 2 - Auto-Generate Your Project
Get `create_claro_project.sh` from the [latest Release](https://github.com/JasonSteving99/claro-lang/releases/latest)
and run this command:
```
$ ./create_claro_project.sh <project name>
```
_Note: The below recording was made with [asciinema](https://asciinema.org/) - try pausing and copying any text._
<script async id="asciicast-SsZNBSFJpmAHgnzEu7Bj29hRu" src="https://asciinema.org/a/SsZNBSFJpmAHgnzEu7Bj29hRu.js"></script>

More details on using this script can be found at [tools/README.md](tools/README.md)

### 2 (Alternative) - Manually Copy Configuration of Example Project
Follow the example Claro project configuration at
[examples/bzlmod/](https://github.com/JasonSteving99/claro-lang/tree/main/examples/bzlmod).

_NOTE_: In your MODULE.bazel file, you'll want to choose the latest release published to:
https://registry.bazel.build/modules/claro-lang

## Your First Claro Program

Continue on to the next section to learn how to build and run your first Claro program!