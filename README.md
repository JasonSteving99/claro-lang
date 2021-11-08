# claro-lang

My hobby-horse programming language where I'll attempt to implement, from
scratch, everything that I dream of having in other languages. This is
likely to be forever in flux, but we'll start somewhere.

Read more detail in the in-progress [design doc](https://docs.google.com/document/d/1JvRXy-UwPjEAzVTCAtmVgBzj-tIEfVwIq6bOa3xGTRk/edit).

# Try it Out!
## Ignore everything below here and jump straight to the actual [src](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro), and follow the real [README](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro#running-claro-programs).


### Compiler implementation decisions
#### Lexer/Parser Generator
Note that this project depends almost entirely on the work in the [JFlex-de Bazel Rules Repo](https://github.com/jflex-de/bazel_rules):

Following [this tutorial](https://tldp.org/LDP/LG/issue41/lopes/lopes.html): 
- Scanner Generator: [JFlex](https://jflex.de/)
    - Here's a [great example](https://tldp.org/LDP/LG/issue41/lopes/lcalc.htm#decl) of JFlex usage, from the above tutorial.
- Parser Generator: [CUP](http://www2.cs.tum.edu/projects/cup/)
    - Here's a [great example](https://tldp.org/LDP/LG/issue41/lopes/ycalc.htm#parser_code) of CUP usage from the above tutorial.
