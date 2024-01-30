# Lambdas are Restricted "Closures"

A "closure" is an anonymous procedure that is able to capture long-lived references to the **variables** defined outside
the body of the lambda, importantly, keeping that reference even as the lambda itself leaves the scope. This is exactly 
how Python or Java lambdas work, for example.

Unfortunately, this leads to hard-to-understand code as you end up with "spooky action at a distance" where calling a
lambda can cause some faraway data to be changed without necessarily realizing or intending for that to be happening. 
This would be fatal for Claro's more advanced "**Fearless Concurrency**" goal, because it represents hidden mutable 
state which would invalidate Claro's goals of guaranteeing that multithreaded code unable to run into data races. 

So, to address these issues, when lambdas reference variables in outer scopes, the variables are captured as a local 
copy of the **current** value referenced by that variable. **Claro's Lambdas have no mechanism to mutate anything not 
passed in as an explicit argument, and they cannot carry any mutable state**.

<div class="warning">

Read more about how Claro prevents data-races [here](../../guaranteed_data_race_free/guaranteed_data_race_free.generated_docs.md).
</div>

### Static Validation

Claro will statically validate that lambdas don't violate the above restrictions:

{{EX1}}

### Captured Variables "Shadow" Variables in the Outer Scope

When a lambda captures a variable from the outer scope, the captured variable inside the lambda is effectively
completely independent from the original variable in the outer scope. It simply "shadows" the name of the outer scope
variable. In this way, lambdas are guaranteed to be safe to call in any threading context as thread-related ordering 
alone can't affect the value returned by the lambda:

{{EX2}}

## Manually Emulating Traditional "Closures"

While Claro's design decisions around Lambdas make sense in the name of enabling "Fearless Concurrency", the 
restrictions may seem like they prevent certain design patterns that may be completely valid when used carefully in a
single-threaded context. But worry not! You can of course implement "closure" semantics yourself (albeit in a more C++
style with explicit variable captures).

{{EX3}}

<div class="warning">

**Note**: The beauty of this design is that even though Claro doesn't prevent you from emulating traditional "closures"
on your own if you so chose, Claro can still statically identify that this `ClosureFn<State, Out>` type is unsafe for
multithreaded contexts and will be able to prevent you from using this to create a data race! 
</div>