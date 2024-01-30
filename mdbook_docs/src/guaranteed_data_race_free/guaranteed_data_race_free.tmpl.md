# Guaranteed Data-Race-Free Concurrency

One of Claro's most powerful advantages is that it is able to statically analyze your concurrent code to ensure that
it is impossible to run into a data-race at runtime.

A data race occurs when two or more threads in a single process access the same memory location concurrently, and at 
least one of the accesses is for writing, and the threads are not using any exclusive locks to control their accesses
to that memory.

While there _are_ situations where a race condition may be desirable, _**they are accidental bugs far more often than 
not**_. So, Claro has been carefully designed to statically prevent you from writing any program with such a data race.
There are a few primary mechanisms in the language that, together, ensure that data races are impossible to encode. At 
their core, these restrictions boil down to preventing any two threads from sharing references to the same mutable data.

<div class="warning">

## Thread-Local Data Can be Mutated at Will
Claro requires the use of immutable data when passing data between threads. By enforcing this constraint globally, Claro
programs in turn receive a **static guarantee that all non-Graph procedure interactions with mutable data are happening
over mutable data that is local to the current thread only** and therefore doesn't require any synchronization
whatsoever.

So, while you'll read about restrictions on Graph Procedures below, keep in mind that the internal implementations of
any given node (e.g. the implementation of a procedure called by a node) may create whatever mutable data it wants, and
mutate it freely, including by passing the data around to other procedures that do the mutation.
</div>

## All Graph Procedure Args Must be Deeply-Immutable

Claro's Graph Procedures are an inherently concurrent control flow structure, with nodes executing concurrently by
definition. Importantly, Graphs are executed on multiple threads using a threadpool, and Claro takes responsibility for
this execution being thread safe. As nodes may be executing simultaneously, it would be fundamentally unsafe for any two
nodes to share a reference to the same mutable data as nothing would prevent one of the threads from mutating the data 
while another thread is reading from it.

Claro's approach to addressing this is to track mutability in the type system, and to make use of that information to
ensure that no two threads ever share mutable state by statically requiring that all Graph procedure arguments and node 
outputs are deeply immutable.

{{EX1}}

<pre class="mermaid">
    graph TD
    nodeB --> res
    nodeC --> res
</pre>

Here, Claro has correctly identified that `nodeB` and `nodeC` would be susceptible to creating a data race, and so a 
compilation error is raised. Additionally, even if there were only a single graph node actually using `mutArg`, it would
still be fundamentally unsafe. Remember that every single node in a graph runs on the Graph Executor, which is backed by
a threadpool meaning that passing any arguments to a graph procedure is inherently an act that hands data to another 
thread. Claro's philosophy of thread safety is to statically prevent sharing mutable state across threads, so this will 
not be allowed.

## All Graph Procedure Node Outputs Must be Deeply-Immutable

You'll also be prevented from introducing a data race by having a graph node pass mutable data to other downstream 
nodes:

{{EX2}}

<pre class="mermaid">
    graph TD
    nodeA --> nodeB
    nodeA --> nodeC
    nodeB --> res
    nodeC --> res
</pre>

Again, Claro has correctly identified that `nodeB` and `nodeC` would be susceptible to creating a data race, and so a 
compilation error is raised.

## Lambdas Cannot Capture Mutable Data

The final restriction that enables "Fearless Concurrency" in Claro programs is the constraint restricting Lambdas from
"closing over"/capturing any mutable value. If Lambdas could capture mutable state data, then passing a Lambda into a
Graph could (very indirectly) circumvent Claro's above restriction on sharing references to mutable data across multiple
threads.

Read more in-depth about this restriction in the 
["Lambdas are Restricted Closures"](../lambdas_and_first_class_procedures/lambda_closures/lambda_closures.generated_docs.md)
section.

## Thread Safe Mutable Data Structures "Blessed" By the StdLib

Claro aims to be a very pragmatic language, and so chooses not to complicate its type system with something like Rust's
(notoriously complex) borrow checker to prevent shared ownership of unsynchronized, mutable data. Instead, Claro opts to
take an approach of statically forbidding the arbitrary sharing of mutable state between threads, but then returning the
ability to do mutation via a curated set of "blessed" mutable data structures that have been manually validated to be
Thread Safe in all contexts.

For example, take the case of a multithreaded web server where it's very common to employ a request cache to improve 
throughput by reusing responses from downstream services for some period of time. This request cache is an inherently
mutable structure (it needs to be updated when a new request needs to be cached, or when reloading an existing cache
entry upon expiration). A mutable request cache is obviously of utmost importance for Claro's practical usefulness as a
language for writing real world web services, so the stdlib exposes Ben Manes' famously high-performance, thread safe 
[Caffeine caching library](https://github.com/ben-manes/caffeine) as the StdLib's 
[`cache` module](https://github.com/JasonSteving99/claro-lang/tree/main/stdlib/cache).

<div class="warning">

## **Important**: This is Restricted to the StdLib
Claro accomplishes this using "Opaque" Types and a compiler intrinsic (trick) to effectively lie about the type's 
mutability to avoid the restrictions on types marked `mut`. In particular, this type is [exported from the 
`cache.claro_module_api` file](https://github.com/JasonSteving99/claro-lang/blob/c3c06329e43c053449b0f43301440c22355b0d93/stdlib/cache/cache.claro_module_api#L2)
as follows:

{{EX3}}

And is internally defined as wrapping the Java `AsyncLoadingCache` type from the Caffeine caching library:

{{EX4}}

Thanks to being defined as an Opaque Type, it's safe for this type to be passed anywhere, even shared between threads,
as users' only mechanism to interact with values of this type is via the "front door" of the procedures exported from
[`cache.claro_module_api`](https://github.com/JasonSteving99/claro-lang/blob/c3c06329e43c053449b0f43301440c22355b0d93/stdlib/cache/cache.claro_module_api)
which define a Thread Safe API.

**It's not possible for user code to actually make this same "lie" about a type's mutability. This feature is explicitly 
restricted to the internal StdLib modules to ensure that Claro's "Fearless Concurrency" guarantees aren't broken by
users either publishing buggy or intentionally malicious modules.** At the moment (and into the foreseeable future), 
Claro places a **much higher** value on being able to make safety guarantees across the entire language ecosystem than 
on any individual's ability to define their own custom mutable data structures that can be shared across threads. 

There are currently no plans to **ever** allow any mutable, user-defined type defined outside the StdLib to be shared 
across threads. Instead, **Claro intends to actively welcome external contributions of high value, general purpose,
demonstrably Thread Safe, mutable data structures to be made available via the StdLib**.  
</div>