# Matching Structured Data

<div class="warning">
Note: Pattern Matching support is currently only partway through it's planned feature support (and has some open bugs to
be addressed). More to come!
</div>

Pattern Matching is much more than just a classic C-style switch statement. In particular, it can be used to match 
arbitrarily structured data.

{{EX1}}

## Matching Arbitrarily Nested Structured Types

Claro supports pattern matching over arbitrary (i.e. `tuple<...>` and `struct{...}`) structured types as their 
structures are fully known at compile time. 

{{EX2}}