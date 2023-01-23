# Types Constrained by Context

Whenever a type is contextually required, the value/expression placed in that position will be type checked to have the
expected type. Otherwise the compiler tries to infer the type.

For example, when you assign a value to a variable declared to have some type, the assigned value must contextually have
the same type as the variable, and Claro will statically type-check that this is true:

```
var i: int = 10;
i = "foo"; # Error. Expected int found string.
```

Alternatively, Claro may infer the type of a newly declared variable instead by checking against the known type of the
value being assigned:

```
var i: int = 10;
var i2 = i; # Ok. Claro infers that i2 must be an int.
```

If the context does not provide enough information for some type to be inferred, you would be required to annotate your
intended type:

```
var unknown; # Error. Each var's type must be set at declaration time.
var known: string; # Ok.
```