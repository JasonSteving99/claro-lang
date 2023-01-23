# Blocking Procedures

Whereas other languages with some form of builtin concurrency mechanism may tend to make it harder to write async code
than blocking code, Claro is very intentional about inverting that balance. Make the good things easy and the bad things
hard. So, you may write blacking code in Claro, but it is really only intended to be used in limited contexts, so Claro
forces your hand. Any function that makes use of the `<-|` operator either directly or indirectly, must be explicitly
annotated to be `blocking`:

```
blocking function doBlocking(...) -> ... {
    ...do stuff...

    var unwrappedGraphRes: Foo <-| fooGraph(...); # Blocking unwrap.

    ...do stuff using `unwrappedGraphRes`...

    return ...;
}
```

To prevent deadlocking, procedures annotated `blocking` may not be called from a Graph. Therefore, you can be confident
that the threading implementation of any logic defined within a Graph Procedure will certainly not suffer from
liveliness issues in the form of deadlocks (of course, you may still write code with bugs such as infinite loops that
may lead to a "livelock").