# Contracts

Consider the example of the generic function:

```
function filter<T>(l: [T], pred: function<T -> boolean>) -> [T] {...}
```

If you really squint, you might notice that there's very little information available in the body of
the `filter<T>(...)` function to tell you about the type T. As a result, you're unable to do much with values of such an
unconstrained generic type beyond passing the value along to another generic function accepting an unconstrained generic
arg, *or* putting it into some collection defined over the same generic type. This would be very limiting if this was
all that could be done with generics.

__Enter Contracts!__ It will take a bit of a buildup, but we should be able to write generic functions that will be able
to put a constraint on the acceptable types by saying something like "this procedure will accept any type, `T`, for
which the function `foo(arg1: T, arg2: T)` exists."

We should be able to write the following generic function:

```
requires(Operators<T>)    # <-- What is this `requires(...)`?
function sum<T>(l: [T]) -> T {
    var res: T = l[0];
    var i = 0;
    while (++i < len(l)) {
        res = Operators::add(res, l[i]); # <-- What is this `Operators::add`?
    }
    return res;
}
```

The function above has a new `requires(...)` clause in the signature which we haven't seen before. This is the mechanism
by which a function constrains the set of types that may be passed into this furction to only types that definitely have
a certain associated procedure implementation existing. The `requires(...)` clause takes in a list of "Contracts" that
must be implemented over the generic type. In this case that contract looks like:

```
Contract Operators<X> {
    function add(lhs: X, rhs: X) -> X;
}
```

This Contract specifies a single function signature that any implementation of this Contract must implement. Other
Contracts may specify more than one signature, or even more than one generic type param. There are no restrictions on
where the generic Contract param(s) may be used in the procedure signatures, so it may even be included in the return
type.