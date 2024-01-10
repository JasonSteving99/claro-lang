# Type Inference

So far, through each code snippet you've seen, each variable has always included an explicit type declaration. This may
be useful for the sake of very explicit readability, however, these type annotations littering your entire codebase may
begin to feel very clunky and inconvenient - particularly when the type is very obvious to the reader, or sometimes if
it becomes very long to type (as the result of many layers of nested collections for example). In almost every case,
however, these explicit type annotations are optional in Claro!

Claro is smart enough to be able to infer the vast majority of types in any given program. So, unless you feel that the
type annotation being present makes the code more readable in a particular situation, then you can generally omit it
entirely! Please keep in mind, however, that while this may indeed make your code visually resemble something like
Python or JavaScript, Claro is 100% statically typed. Therefor, in this regard, Claro is much more alike
Rust/Java/Haskell than it is like any dynamic language. And, importantly, Claro is *not* an "Optionally Statically
Typed" language like Typescript - the compiler must always statically know the type of every value, you may at times
simply choose to avoid explicitly including the type annotation in the source code.

## Examples

Instead of:

{{EX1}}

You could write:

{{EX2}}

Each corresponding statement has exactly the same meaning. They differ only syntactically. Each variable is still
declared to have the same static type you'd expect.
