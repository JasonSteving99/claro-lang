# Lambdas are "Closures" (for now)

Technically a "Closure" is a lambda that is able to capture long-lived references to the values defined outside the body
of the lambda, importantly, keeping that reference even as the lambda itself leaves the scope (passed into another scope
or returned). This is exactly how Python lambdas work, for example.

Unfortunately, this leads to hard-to-understand code as you end up with "spooky action at a distance" where calling a
lambda can cause some faraway data to be changed without realizing it. For Claro's more advanced "Fearless Concurrency"
goals, this is even worse because it represents hidden mutable state which would invalidate Claro's goals of making
multithreaded code unable to run into data races. Instead, to solve this, when lambdas reference names in outer scopes,
they make a local copy, and can't mutate the outer scope.

### The Bad (The TODO)

*The below example demonstrates the main current implementation flaw of lambdas which will be updated to ensure that
lambdas are always pure functions:*

```
var i = 0;
var f: function<int -> int> = x -> {
    i = i + x; # `i` is captured, and also locally updated. Will be impossible in the future. 
    return i;
};

print(f(0)); # 0   <-- `f` is stateful and mutates its internal state on each call.
print(f(1)); # 1
print(f(5)); # 6
print(f(5)); # 11  
print(i);    # 0   <-- at least `i` is still unchanged.
```