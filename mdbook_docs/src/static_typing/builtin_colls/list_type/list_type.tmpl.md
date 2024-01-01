# Lists

The simplest collection type allows you to keep an arbitrary number of values in some ordering. The list is very much
like a Python list in that it allows arbitrary appends, and random access to read values at a O-based index. Unlike
Python, as Claro is statically typed, all values in the list must be of the same type, and this type must be
pre-determined upon declaration of the variable which reference the list.

{{EX1}}

## Accessing List Elements

Lists support random-access indexing via traditional C-like syntax:

{{EX2}}

## Mutable List Element Reassignment

You can update the individual values stored at a particular list index via traditional C-like syntax:

{{EX3}}

## Index-Out-Of-Bounds

As with most other languages that allow random-access to lists, you must be careful to always index into lists at valid
positions. Any accesses of index, i, where i < 0 or i >= len(l) will result in the program Panicking (exiting in an 
unrecoverable way).

```
var l = mut [1, 2];
l[99] = 0;    # <-- Panic: Index-out-of-Bounds!
```

(Note: it's possible that as the language evolves, Claro may instead opt to make all list subscripting operations
inherently safe by returning some structured result that models the possibility that the list index was invalid. This is
ideal for safety, however, this would impose a global runtime overhead so the tradeoff is still being evaluated.)

## Stdlib `lists` Module 

A large variety of list operations are available in the 
[stdlib's `lists` module](https://github.com/JasonSteving99/claro-lang/tree/main/stdlib/lists). For example, the previous exapmle
added an element to the end of a mutable list by using the `lists::add` procedure whose signature is the following in
the `lists.claro_module_api` file:

```
# Appends the specified element to the end of this list.
consumer add<T>(l: mut [T], toAdd: T);
```

## Empty Lists

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
# Hypothetically, Claro could infer that the type of `l` is `mut [string]` based
# solely on the usage of `l` later on.
var l = mut [];

...a bunch of code...

append(l, "foo");
```