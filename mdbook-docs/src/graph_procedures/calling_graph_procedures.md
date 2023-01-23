# Calling Graph Procedures

As you've already seen, if you call a Graph Procedure from within another Graph (composition) then Claro will
automatically handle the scheduling for you so that downstream nodes receive the value when it's ready. If you tried
calling a Graph Procedure from the top-level of a file, or from a non-Graph Procedure, then you'll see you receive a
value wrapped in a `future<...>`. This is because the Graph Procedure may not be done running yet, as Claro follows the
Async pattern for concurrent execution.

```
var graphRes: future<Foo> = fooGraph(...);
```

There's not much you can do with a `future<...>` as it's really just a handle representing work whose result you'd like
to be able to access when it's ready. In this situation (outside a Graph), as a `future<...>` represents some
computation that may not be done yet, the __only__ way to get the actual result is to block the current thread until the
other threads running the graph backing the `future<...>` have finished. To do so, use the "blocking unwrap" op `<-|`:

```
var unwrappedRes: Foo <-| fooGraph(...);
```

The number one thing to keep in mind is that between calling a Graph and blocking on its result, any operations between
*may* be running concurrently with the graph backing the `future<...>` (you don't know when the graph actually finishes
except that it will certainly have finished after the `<-|` operation).

```
var graphFuture: future<Foo> = fooGraph(...);

# These two instructions are likely running concurrently with respect to
# `graphFuture`, as `graphFuture` likely hasn't finished yet, but they are
# definitely serialized with respect to each other.
doSomething(...);
doAnotherThing(...);

# Blocking the current thread to "unwrap" the `future<Foo>` into a raw `Foo`
# value we can operate on.
var unwrapped: Foo <-| graphFuture; 
```