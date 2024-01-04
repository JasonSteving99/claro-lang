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

## "Default" Case

The example above makes use of a "default" case that will match anything that the cases preceding it didn't match.

{{EX3}}

In the context of pattern matching, the `_` token represents a "wildcard" pattern. Learn more about this in the 
[Wildcard Patterns section](./wildcards/wildcards.generated_docs.md).

## Multi-Statement Cases

When a `case` is matched, the associated code block following the `->` and preceding the next `case` (or until the 
overall closing `}`) will all be executed. This code block can contain any number of statements.

{{EX4}}

## Patterns Must Not Reference Existing Variables

<div class="warning">
While this may seem like an arbitrary restriction, this is actually necessary in order to ensure that Claro's static
exhaustiveness and case reachability checks are actually guaranteed to be correct. Technically, it would be possible for
Claro to loosen this restriction, but this is a conscious, opinionated design choice to limit the number of special 
cases to keep in mind when writing or reading a match statement.
</div>

The following is invalid:

{{EX5}}

_Note: Claro's error messaging is a work in progress - the above error message will be improved._