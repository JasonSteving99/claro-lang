# Structs

Structs are similar to tuples with named field values of arbitrary pre-declared types.

{{EX1}}

## Field Access

Struct field values can be directly accessed using "dot-notation" as below:

{{EX2}}

## Mutable Structs

Just like any other builtin collection type, a Claro struct may be declared mutable using the `mut` keyword when 
declaring a variable or initializing the value. You may then update element values at will as long as the initial type 
declaration for each element is honored.

{{EX3}}
