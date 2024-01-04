# Pattern Matching

<div class="warning">
Note: Pattern Matching support is currently only partway through it's planned feature support (and has some open bugs to
be addressed). More to come!
</div>

In addition to the typical if-else style branching construct, Claro offers a more powerful construct called "Pattern 
Matching". In its simplest form, Pattern Matching can simply be used as a more concise replacement for if-else chains.

Compare the following if-else chain:

{{EX1}}

with the comparable match-statement:

{{EX2}}

The `match` statement takes in a single expression of any type, to be compared against the given `cases` clauses in 
order - the first one to successfully match is executed (there is no fallthrough like in a Java or C++ style switch).

## Multi-Statement Cases

When a `case` is matched, the associated code block following the `->` and preceding the next `case` (or until the 
overall closing `}`) will all be executed. This code block can contain any number of statements.

{{EX3}}