# (Advanced) Impossible-to-Initialize Recursive Alias Type Definitions

Some recursive aliases like the following should be rejected at compile-time because they're impossible to instantiate.
The issue with these aliases is that the type recursion has no implicit "bottom" and implies an infinitely nested value.
Because it's impossible to ever initialize a value composed of infinitely many values (you'd never finish typing the
code), Claro lets you know right away at compile time that the infinite type is rejected for being unusable.

The below alias definitions all trigger compile-time warnings from Claro indicating that these types aren't usable and
are therefore illegal.

```
alias IllegalUnboundedRecursiveAlias : tuple<int, IllegalUnboundedRecursiveAlias>
alias InfiniteRecursion : InfiniteRecursion
alias PartialUnbounded : tuple<PartialUnbounded, [PartialUnbounded]>
```

Example error message:

```
Impossible Recursive Alias Type Definition: Alias `IllegalUnboundedRecursiveAlias`
represents a type that is impossible to initialize in a finite number of steps. To
define a recursive type you must ensure that there is an implicit "bottom" type to
terminate the recursion. Try wrapping the Alias self-reference in some builtin
empty-able collection:
	E.g.
		Instead of:
			alias BadType : tuple<int, BadType>
		Try something like:
			alias GoodType : tuple<int, [GoodType]>
...
```
