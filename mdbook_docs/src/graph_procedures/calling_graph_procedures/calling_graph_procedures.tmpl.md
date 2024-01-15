# Calling Graph Procedures

As you've already seen, if you call a Graph Procedure from within another Graph (composition) then Claro will
automatically handle the scheduling for you so that downstream nodes receive the value when it's ready. If you tried
calling a Graph Procedure from the top-level of a file, or from a non-Graph Procedure, then you'll see you receive a
value wrapped in a `future<...>`. This is because, as Claro follows the Async pattern for concurrent execution, some
nodes in the Graph Procedure may not be done running yet meaning that the overall Graph result may not be ready either.

For example, the `getWatchlist` Graph Procedure defined [earlier](../graph_procedures.generated_docs.md#fig-1) could be 
called as if it were a typical procedure call:

{{EX1}}

There's not much you can do with a `future<...>` as it's really just a handle representing work whose result you'd like
to be able to access when it's ready. In this situation (outside a Graph), as a `future<...>` represents some
computation that may not be done yet, the __only__ way to get the actual result is to block the current thread until the
other threads running the graph backing the `future<...>` have finished. To do so, use the "blocking unwrap" op `<-|`:

{{EX2}}

## Graphs Execute off the "Main" Thread

The number one thing to keep in mind is that between calling a Graph and blocking on its result, any operations between
*may* be running concurrently with the graph backing the `future<...>` (you don't know when the graph actually finishes
except that it will certainly have finished after the `<-|` operation).

{{EX3}}