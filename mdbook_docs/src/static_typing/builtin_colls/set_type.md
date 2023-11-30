# Sets

Claro sets are much like Python sets, with a fixed, single type for all elements. You may initialize them with many
elements and then check for membership in the set later.

```
var mySet: {int} = {1, 6, -12};
print(10 in mySet); # false
print(6 in myset); # true
```

(__Note__: for now the usefulness of sets is very limited as the available operations are extremely limited. A serious
TODO is open to support all expected se operations: add, remove, union, intersect, etc.)
