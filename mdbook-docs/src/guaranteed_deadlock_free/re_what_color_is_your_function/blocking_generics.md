# Blocking Generics

You are able to define a procedure whose "blocking"-ness is generically determined by the type of the first class
procedure arg that the function is called with. Taking inspiration
from [Rust's Keyword Generics Initiative](https://blog.rust-lang.org/inside-rust/2022/07/27/keyword-generics.html), a
Claro procedure may be declared "Blocking-Generic" with the following syntax:

```
blocking:pred function filter<T>(
    l: [T], pred: blocking? function<T -> boolean>) -> [T] {...}
```

Now, with only a single implementation of your `filter` function, calls may be statically determined to be either a
blocking or non-blocking call depending on the type of the passed pred function arg. So now, from within a Graph, you
may call this "blocking-generic" function as long as you pass in a non-blocking pred function.

### Note on the `blocking:argName` and `blocking?` Syntax

Claro localizes Generics only to procedure signatures. This is done with the intention of making Generics more easily
understandable, such that Generics itself may be conceptualized simply as a form of "templating" (regardless of whether
or not this is how the compiler is *actually* implementing the feature).

As a result, these type modifier syntaxes are restricted to be used within top-level procedure definition signatures
only. In particular, you may not define a variable of a blocking generic type:

```
# Illegal use of `blocking:...`, and `blocking?` outside of top-level Proc. definition.
var myBlockingGenericFn:
    blocking:arg1 function<|[int], blocking? function<int -> boolean>| -> [int]>;
```

This has the implication that lambdas may not make use of blocking generics. But this is in line with Claro's single-use
intention for lambdas, encouraging the definition of lambdas that will only be used in a single limited scope. For any
cases that actually need to make use of blocking-generics, you should define a top-level procedure.

You can, however, still make first-class references to top-level blocking-generic procedures in order to pass them
around as data. The only restriction, is that you must statically declare which blocking variant the reference will take
on:

```
# A blocking function var, to which you may *only* pass blocking functions.
var myBlockingFn: blocking function<
        |[int], blocking function<int -> boolean>| -> [int]>
    = filter;

# A non-blocking function var, to which you may *only* pass non-blocking functions.
var myNonBlockingFn: function<|[int], function<int -> boolean>| -> [int]>
    = filter;
```