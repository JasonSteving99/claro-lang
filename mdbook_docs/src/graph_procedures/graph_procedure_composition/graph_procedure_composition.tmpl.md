# Graph Procedure Composition

Great! Now Graph Procedures have given us free concurrency just by structuring our code declaratively rather than
imperatively. But as we'd realistically only want to put a few nodes in a single Graph Procedure from a code maintenance
and readability point of view, how do we write DAGS that are larger than just a few nodes? Composition! By this I mean
simply calling another Graph Procedure from within the current one.

For Example:

{{EX1}}

<pre class="mermaid">
    graph TD
    barC(barC) --> barA
    barB(barB) --> barA
    barA(barA) --> barRes
</pre>

{{EX2}}

<pre class="mermaid">
    graph TD
    fooC(fooC) --> fooA
    fooC(fooC) --> fooB
    fooA(fooA) --> fooRes
    fooB(fooB) --> fooRes
</pre>

Because `foo(...)` includes a call to `bar(...)` as a subgraph, you can imagine that node `fooB` in graph `foo` actually
composes around the entire `bar` graph.

<pre class="mermaid">
graph TD
    fooC(fooC) --> fooA
    fooC --> fooB
    fooB --> fooRes
    subgraph fooB
        barC(barC) --> barA
        barB(barB) --> barA
        barA(barA) --> barRes
    end
    fooA(fooA) --> fooRes
</pre>

This composition is extremely simple to understand in this way. The entire subgraph is started after all data
dependencies of the node wrapping it are ready.
