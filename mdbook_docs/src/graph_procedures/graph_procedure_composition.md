# Graph Procedure Composition

Great! Now Graph Procedures have given us free concurrency just by structuring our code declaratively rather than
imperatively. But as we'd realistically only want to put a few nodes in a single Graph Procedure from a code maintenance
and readability point of view, how do we write DAGS that are larger than just a few nodes? Composition! By this I mean
simply calling another Graph Procedure from within the current one.

For Example:

```
graph function bar(argB: ..., argC: ...) -> future<...> {
    root barRes <- doBar(@b1);
    node b1 <- doBar1(@b2, @b3);
    node b2 <- doBar2(argB);
    node b3 <- doBar3(argC);
}
```

<pre class="mermaid">
    graph LR
    b3(b3) --> b1
    b2(b2) --> b1
    b1(b1) --> barRes
</pre>

```
graph function foo(argA: ...) → future<...> {
    root fooRes <- doFoo(@f1, @f2);
    node f1 <- doFoo1(@f3);
    node f2 <- bar(10, @f3); # <-- Graph Composition via Call to `bar`.
    node f3 <- doFoo3(argA);
}
```

<pre class="mermaid">
    graph LR
    f3(f3) --> f1
    f3(f3) --> f2
    f1(f1) --> fooRes
    f2(f2) --> fooRes
</pre>

Because `foo(...)` includes a call to `bar(...)` as a subgraph, you can imagine that node `f2` in graph `foo` actually
composes around the entire `bar` graph.

<pre class="mermaid">
graph TD
    f3(f3) --> f1
    f3 --> f2
    f2 --> fooRes
    subgraph f2
        b3(b3) --> b1
        b2(b2) --> b1
        b1(b1) --> barRes
    end
    f1(f1) --> fooRes
</pre>

This composition is extremely simple to understand in this way. The entire subgraph is started after all data
dependencies of the node wrapping it are ready.
