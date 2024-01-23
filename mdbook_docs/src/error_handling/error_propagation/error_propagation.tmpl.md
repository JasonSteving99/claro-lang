# Error Propagation via `?=` Operator

As mentioned in the previous section, the power of Claro's builtin `std::Error<T>` type comes from the special treatment
that the compiler gives to values of that type. Specifically, Claro gives you the ability to early-return an error value
from a procedure. If for some reason a procedure has no way of actually handling a potential error itself, it can opt to
delegate the handling of the error to any callers. This allows the procedure doing error propagation to be written to
handle only the "happy path".

This example demonstrates a procedure that propagates potential errors to its callers:

{{EX1}}

<div class="warning">

**Note**: The error propagation above doesn't allow the caller to know details about whether the error came from the 
first or second call to `safeGet()`. This may or may not be desirable - but the design space is left open to Claro users
to decide how they want to signal errors to best model the noteworthy states of their problem domain.
</div>

## `?=` Operator Drops All Error Cases

You can observe in the above example that the `?=` operator will propagate any `std::Error<T>` found on the 
right-hand-side of the assignment. So, as a result, the value that reaches the variable on the left-hand-side of the
assignment will drop all `std::Error<T>` variants from the `oneof<...>`. 

Below, some examples are listed to indicate the resulting type of the `?=` operator:

{{EX2}}