# Lists

The simplest collection type allows you to keep an arbitrary number of values in some ordering. The list is very much
like a Python list in that it allows arbitrary appends, and random access to read values at a O-based index. Unlike
Python, as Claro is statically typed, all values in the list must be of the same type, and this type must be
pre-determined upon declaration of the variable which reference the list.

```
{{EX1}}
```

### Empty Lists

It's worth noting that Claro has no way of inferring the correct element type of an empty list when it's type is not
constrained by context. For example, the below variable declaration would be a compile-error:

```
var l = []; # Compiler Error: ambiguous type.
```

### Empty List Type Inference By Later Usage (Will Never Be Supported)

You might think that Claro should be able to infer the type intended for this empty list based on the later usage of the
variable it's assigned to. __Claro takes the opinionated stance that this would be inherently undesirable behavior__.
Type inference shouldn't follow some esoteric resolution rules. It would be all too easy to implement a complex type
inference system that can infer types far better than any real world human reader could - the end result would simply be
enabling code to be written that is intrinsically difficult for your colleagues (and your future self) to read later on.
This is an anti-goal of Claro.

__The following will never be supported__:

```
# Hypothetically, Claro could infer that the type of `l` is [string] based
# solely on the usage of `l` later on.
var l = mut [];

...a bunch of code...

append(l, "foo");
```