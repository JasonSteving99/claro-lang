<div align="left">
  <img src="https://github.com/JasonSteving99/claro-lang/blob/main/logo/ClaroLogoFromArrivalHeptapodOfferWeapon1.jpeg" width=200 height=200>
</div>

# claro-lang

My hobby-horse programming language where I'll attempt to implement, from
scratch, everything that I dream of having in other languages. This is
likely to be forever in flux, but we'll start somewhere.

# Read the [Comprehensive User Docs](https://jasonsteving99.github.io/claro-lang/)
Please understand that these docs are a work in progress, and while they do cover a large chunk of the language features, there is still more documentation to come including better examples and clearer explanations.

# Learn Claro By Example! 
Check out the [example Claro programs](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro/claro_programs).

# Try it Out in a GitHub Codespace!
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/JasonSteving99/claro-lang?quickstart=1)

Once you're in VSCode in your codespace, navigate to
```
src/java/com/claro/claro_programs/BUILD
```
and you should be able to run any of the `claro_binary` Bazel Build targets by clicking the `Run` button rendered inline above the target.

## In case the VSCode Bazel Plugin Isn't Working
...you can always manually run the programs manually by finding the corresponding build target name and running a command like the following:
```
bazel run --nojava_header_compilation //src/java/com/claro/claro_programs:<replace this with your desired build target>
```

# Try it Out Online at [riju.codes/claro](https://riju.codes/claro)!
Please keep in mind that in the current state of the world, Riju is generally behind the latest state of Claro development since I don't control Riju and can't redeploy for each new commit to this repo. If you want the latest of the latest then read the below to build Claro locally. 

# Build Claro Locally!
View the [src](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro), and follow the real [User Guide](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro#running-claro-programs).
