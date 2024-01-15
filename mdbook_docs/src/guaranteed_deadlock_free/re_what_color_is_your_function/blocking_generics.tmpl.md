# Blocking Generics

You're able to define a procedure whose "blocking"-ness is generically determined by the type of the first-class
procedure arg that the function is called with. Taking inspiration
from [Rust's Keyword Generics Initiative](https://blog.rust-lang.org/inside-rust/2022/07/27/keyword-generics.html), a
Claro procedure may be declared "Blocking-Generic" with the following syntax:

{{EX1}}

Now, with only a single implementation of your `filter` function, calls may be statically determined to be either a
blocking or non-blocking call depending on the type of the passed `pred` function arg. So now, from within a Graph, you
may call this "blocking-generic" function as long as you pass in a non-blocking `pred` function.

### Note on the `blocking:argName` and `blocking?` Syntax

Claro localizes Generics only to procedure signatures. This is done with the intention of making Generics more easily
understandable, such that Generics itself may be conceptualized simply as a form of "templating" (regardless of whether
this is how the compiler is *actually* implementing the feature).

As a result, these type modifier syntaxes are restricted to being used within top-level procedure definition signatures
only. In particular, you may not define a variable of a blocking-generic procedure type:

{{EX2}}

### Lambdas Cannot Use Any Form of Generics

This has the implication that lambdas may not make use of blocking generics. But this is in line with Claro's single-use
intention for lambdas, encouraging the definition of lambdas that will only be used in a single limited scope. For any
cases that actually need to make use of blocking-generics, you are by definition defining a procedure that should have
more than one use case, and you should define a top-level procedure instead.

### First-Class References to Blocking-Generic Top-Level Procedures

You can, however, still make first-class references to top-level blocking-generic procedures in order to pass them
around as data. The only restriction, is that you must statically declare which blocking variant the reference will take
on:

{{EX3}}