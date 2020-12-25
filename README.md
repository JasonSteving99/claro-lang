# claro-lang

My hobby-horse programming language where I'll attempt to implement, from
scratch, everything that I dream of having in other languages. This is
likely to be forever in flux, but we'll start somewhere.

Read more detail in the in-progress [design doc](https://docs.google.com/document/d/1JvRXy-UwPjEAzVTCAtmVgBzj-tIEfVwIq6bOa3xGTRk/edit).

Compiler implementation decisions: 
- Scanner Generator: [JFlex](https://jflex.de/)
    - Here's a [great example](https://tldp.org/LDP/LG/issue41/lopes/lcalc.htm#decl) of JFlex usage, from [this tutorial](https://tldp.org/LDP/LG/issue41/lopes/lopes.html).
- Parser Generator: [CUP](http://www2.cs.tum.edu/projects/cup/)
