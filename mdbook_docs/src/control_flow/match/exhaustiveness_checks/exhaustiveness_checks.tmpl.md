# Static Exhaustiveness Checks

Pattern Matching is **not** just convenient syntax sugar. By using a `match` statement instead of an if-else chain, 
Claro is able to statically validate that every possible case is exhaustively handled by some `case`.

For example, the following `match` over a value of type `oneof<Red, Yellow, Green>` is missing a `case` to handle the 
`Green` atom and Claro rejects the `match` at compile-time:

{{EX1}}

By following the suggestion in the error message above, we can fix the program:

{{EX2}}

## Non-Trivial Exhaustiveness Checks Example

The above example is fairly trivial, just validating that all `oneof` type variants are handled. However, Claro's
exhaustiveness checks are fairly sophisticated, and should be able to catch mistakes in much more complicated scenarios:

{{EX3}}

Again, following the suggestion from the error message, we can fix the program:

{{EX4}}

_Note: Claro's suggestions for resolving non-exhaustiveness_ `match` _statements are intelligent and reliable, but Claro
will only warn about a single missing case example at a time (even if there are multiple unhandled cases). You may have
to apply multiple suggestions in succession, but simply following the suggestions will definitely (eventually) lead to a
fully exhaustive match statement._