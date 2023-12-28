# Generics

Oftentimes, you'll find that some code patterns keep coming up over and over and you'll want to find same way to factor
out the major commonalities in logic from the minor specific details that you'd want to just plug in as needed. For
example, you might realize that you're writing loops to filter lists based on conditions all over your code; the only
difference between the implementation in any of these occurrences of filtering being the element types and the specific
condition. But because you want to filter lists of all kinds of types you might not immediately think you could write a
single function that could be called wherever filtering is needed. __Enter Generics!__

{{EX1}}

The function `reduce<A, B>(...)` is defined to take a list of elements of some arbitrary (generic) type, `A`, and an 
accumulation function that takes in the current accumulated value, of type `B`, and the current element of that generic
type, `A`. In this example, the particular types `A` and `B` are "unconstrained". The only constraint is the typical 
constraint that the given function's first arg must have the same type as the initial accumulated value, and the second
arg must have the same type as the elements of the reduced list.

So, the generic types take on the "concrete" types of the data that happens to be passed into the function's callsite:

{{EX2}}