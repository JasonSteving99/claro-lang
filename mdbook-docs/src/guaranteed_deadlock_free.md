# Guaranteed Deadlock-Free Concurrency

One of Clard's most powerful advantages is that it is able to statically analyze your concurrent code to determine that
it is impossible to run into a deadlock at runtime.

A deadlock is a situation where a thread blocks its execution waiting for another thread to complete or for some other
action to complete before it can continue, but the the other thread or action never completes thereby leaving the
waiting thread permanently blocked. Threads are not free, and effectively losing access to a deadlocked thread has
costlier implications than just losing that unit of work completing. Each thread costs about 1MB of RAM and in a server
application deployed with a fixed number of threads, losing even one can lead to cascading failures such as thread
starvation (having no more threads in a healthy state available do meaningful work) or simply falling behind on incoming
request handling, leading to a server decreasing its effective throughput, causing other servers to pick up the load (
making them more likely to fail in turn) or just straight up dropping user requests returning errors to them and
degrading product experience.

To mitigate these risks at scale, high-throughput, low-latency services turn to the __async__ concurrency pattern to
handle all operations in a non-blocking way. __Claro's Graph Procedures implement the async pattern for you for free,
while statically validating that your concurrent code is entirely non-blocking__. It does so by modeling every Graph
node as an async operation that will not even be started until *after* all of its data dependences are resolved. Once a
node is ready for execution it will be scheduled on a threadpool with as many threads as available CPU cores (will be
configurable in the future).

In this way, calling a Graph Procedure is actually an extremely lightweight operation from the perspective of the
calling thread. The calling thread simply

1. traverses the Graph (without executing any nodes)
2. composes a `future<...>` representing a handle to the work to be done by the Graph
3. submits the Graph to the Graph Executor to schedule on its threadpool when threads become available

After these steps the calling thread is completely freed to move on, knowing that the work represented by the Graph
Procedure's returned `future<...>` will be handled by other threads. As a result, in a web server, after calling a
request handling Graph the service thread is free to just immediatley move on to accepting new requests. The service
thread never needs to block to wait for request handling business logic to complete. Now, a server built using this
approach will no longer be bound by the number of incoming requests as it will be able to continuously schedule incoming
requests to be processed when Graph Executor threads become available. Of course, the server may still fail due to heavy
load, though this will end up coming from OOMs (out-of-memory errors) as the result of storing all of the queued
requests. Even so, as a general rule, this will happen much later than if you were to execute request handling logic
using thread blocking operations, and it will almost always degrade more gracefully when it does eventually reach its
limit.

The only concession that you, as a programmer, have to make is simply defining all of your concurrent logic inside a
Graph Procedure. Claro will then manage all of the scheduling for you, while enforcing that you never block one of the
Graph Executor threads (you may not use the `<-|` operator in any code transitively reachable from your Graph, or else
you'll receive a compiler error). To provide a single, well-lit path for safely designing scaleable services in Claro,
the only available mechanism to access threads are Graph Procedures.