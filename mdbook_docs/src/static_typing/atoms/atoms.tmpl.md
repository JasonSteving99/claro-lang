# Atoms

Sometimes it's useful to represent a concept that doesn't necessarily have any meaningful "data" apart from a name. For
example, you may want to represent the states of a simple street-light (i.e. red, yellow, or green). 

Claro's atoms provide a clean solution for modelling these states:

{{EX1}}

Now, you can write code that directly uses these `Red`, `Yellow` and `Green` as values. 

{{EX2}}

## Static Validation

<div class="warning">
You could try to use strings for this purpose, but then you would need to do runtime string equality checks throughout 
your codebase to distinguish one state from another as their types would all be the same, `string`, and even worse you 
open yourself to simple typo bugs.
</div>

Using atoms, Claro will catch any accidental typos for you at compile-time:

{{EX3}}

## Ad-Hoc "Enums"

Unlike many other languages, if you want to define a type that has only a limited set of possible values you don't have
to declare an "enum" ahead of time. Instead, Claro encourages modeling this using the builtin `oneof<...>` type as in
the example above. It can be useful to define an alias to represent the "enum" in a concise way if it's widely used:

{{EX4}}
