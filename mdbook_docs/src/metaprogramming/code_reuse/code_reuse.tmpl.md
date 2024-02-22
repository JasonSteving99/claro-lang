# (Literal) Code Reuse

<div class="warning">

Claro enables you to **_literally reuse source code_** throughout your program rather than offering a list of complex
type system features that **_indirectly enable code reuse_** like other languages do (e.g. via inheritance in OO
languages, or liberal use of higher order functions in FP languages).

It will take a bit of conceptual groundwork (unlearning long-held assumptions) to understand Claro's approach here, but
in exchange, you'll be given some powerful new ways to think about "_what_" a Claro program is. 
</div>

Rather than trying to impose specific _code organization_ design patterns on you (e.g. Java trying to force use of
inheritance) Claro instead aims to be flexible enough to allow you full control of using _and encoding_ your own
organizational design patterns (potentially including inheritance if you felt so inclined).

Probably the most fundamental idea that you'll need to internalize to fully understand Claro's larger design in a deep
way is the relationship that a file containing Claro source code actually has with the final resulting program. This is
a subtle point. It's very possible to write a good amount of Claro code without noticing anything unusual in this
regard. 

Rather than going into an overly detailed explanation, read on to the following sections for some detailed 
examples of various different ways you can dynamically construct Claro programs at Build time.
