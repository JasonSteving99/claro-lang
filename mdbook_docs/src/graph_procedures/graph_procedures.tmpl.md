# Graph Procedures

A __Graph Procedure__ is much like a regular Procedure, with the only difference coming in how you structure code in
the body. As its name implies, the body of a Graph Procedure will be structured as a graph of operations. Specifically
it is a DAG (directed-acyclic-graph) where each node in the DAG represents some isolated unit of work which may depend
on data produced by one or more other nodes and will produce its own resulting data. This structure is inherently
parallelizable as Claro can analyze the provided DAG to schedule nodes to run as soon as possible once all of the data
depended on by that node is ready. If any two nodes happen to have all of their dependent data ready at the same time,
then Claro may schedule those nodes to run concurrently.

In fact, not only does Claro enable concurrency, it actually is able to create the optimal schedule to run your nodes.
You don't need to think about scheduling at all, simply encode the data relationships between your operations, and Claro
does the rest.

All of this is achieved by scheduling nodes to run cooperatively on a threadpool currently configured to have a single
thread per CPU core (as of this writing, this default is the only option, but it will become configurable in the
future (i.e. Google Java services default to 50 request threads)). This allows you to trivially achieve significantly
better utilization of your available hardware resources than single threaded code, and much more safely and more easily
than can generally be achieved with a handcrafted threaded program.

The example below shows syntax vs DAG visualization:

{{EX1}}

As you can see clearly in the diagram below, `profile` must run first but `movies` and `shows` may be computed
concurrently:

<pre class="mermaid">
    graph TD
    profile(profile) --> movies
    profile --> shows
    movies(movies) --> recWatchList
    shows(shows) --> recWatchList
</pre>
