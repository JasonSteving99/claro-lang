# Sets

Claro sets are much like Python sets, with a fixed, single type for all elements. You may initialize them with many
elements and then check for membership in the set later.

{{EX1}}

## Adding Elements to a Set

Elements can be added to a set by making use of the `sets::add` function from the
[stdlib's `sets` module](https://github.com/JasonSteving99/claro-lang/tree/main/stdlib/sets).

```
# Adds the specified element to this set if it is not already present. If this set already contains the element, the
# call leaves the set unchanged and returns false. This ensures that sets never contain duplicate elements.
#
# Returns: true if this set did not already contain the specified element.
function add<T>(s: mut {T}, t: T) -> boolean;
```

{{EX2}}

