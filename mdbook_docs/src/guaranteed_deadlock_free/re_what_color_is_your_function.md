# Re: "What Color is Your Function?"

(*For context, the blog post
["What Color is Your Function?"](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/) by Bob
Nystrom is highly recommended reading.*)

Unfortunately, introducing the `blocking` procedure type variant has the effect of "coloring" all functions that
transitively reach a `blocking` procedure. This ends up being a problem for any code that provides some generic
functionality over first-class procedure arguments that we would ideally like to be able to reuse and call from any
context, whether blocking or not.

Take, for example, Functional Programming's common `filter` function with the following signature:

```
function filter<T>(l: [T], pred: function<T -> boolean>) -> [T];
```

As currently defined, the `filter` function with the above signature could only be used over non-`blocking` pred
function args. You'd need to write a duplicate function explicitly accepting a `blocking` pred function in its signature
if you wanted to filter lists using a pred function that makes use of blocking operations:

```
blocking function filterBlocking<T>(
    l: [T], pred: blocking function<T -> boolean>) -> [T];
```

This duplication would be pervasive throughout functional-style code, and would discourage using functional-style at
all. Both of which are very undesirable outcomes. So, Claro handles this using one more form of generics inspired
by [Rust's Keyword Generics Initiative](https://blog.rust-lang.org/inside-rust/2022/07/27/keyword-generics.html),
"Blocking Generics".
