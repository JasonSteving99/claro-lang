# Performance Optimizations

As Claro's builtin `copy(...)` performs a **deep copy**, performance becomes an important consideration when data can 
become arbitrarily large (whether as a result of a deeply nested type or not). Fortunately, Claro is able to perform one
significant optimization that can have an incredible effect on the runtime performance of copying large data structures.

## Claro's `copy(...)` is Aware of Mutability

The key observation that enables this performance optimization is that, as Claro does not expose a value's memory 
location to users, if a piece of data is deeply-immutable (and in a few other situations that Claro takes advantage of),
there is no possible way to distinguish between the two situations below:
1. having equal values located at different addresses in memory
2. having "shared references" to the exact same value in memory

Claro takes advantage of this fact to generate the most efficient possible code to copy the specific type in question.
It does so by eliminating any _actual_ copying of deeply immutable data found nested anywhere within a copied value.

For example, take the below **mutable** list containing **immutable** lists. When it is copied, a new mutable list must
be initialized to represent the outer list so that the original and copied values may be mutated independently. However,
the internal immutable lists can just be referenced directly in the copied list (thus establishing what are known as 
"shared references" to the underlying memory). 

{{EX1}}

## Demonstrating the Performance Win

Again, I'll reiterate that it's impossible to _directly observe_ from Claro code itself that this optimization has taken
place as Claro doesn't provide any mechanism for actually checking a value's memory address. So, instead, I'll try to
demonstrate _indirectly_ that this optimization must actually be occurring. 

The below example sets up an experiment where a very large, nested list is populated and then copied twice. The first 
copy is done manually using list comprehension. Then, the second copy uses the builtin `copy(...)`. Each copy is timed
to get a sense of the impact of this optimization. 

To make things interesting, the outermost level of the list is mutable so that the overall copy is **not a no-op**. 
However, the performance gain comes from being able to avoid the unnecessary copies **all** of the inner lists. 

<div class="warning">

**Note**: I'm not claiming that this is a rigorous "benchmark" of any sort - just that this broadly demonstrates the 
claim.

</div>


{{EX2}}