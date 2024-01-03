# Parameterized Types

Claro supports the definition of types that may be parameterized with a generic type parameter so that they can be used
to contain arbitrary types. For example the following is a definition of a type `Maybe<T>` that has the generic type
param `T`:

{{EX1}}

This type definition is useful for describing the generic concept of a value that may or may not be present, without
needing to define repeated declarations for each specific type that may or may not be present:

{{EX2}}


## Generic Type Param Must be Referenced in Type Declaration

<div class="warning">
The generic type param must be referenced somewhere in the type definition or Claro will statically reject the 
definition with an explanation of the problem.
</div>

{{EX3}}