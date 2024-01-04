# For Loops

For loops in Claro are closely analogous to Java's 
["enhanced for-loops"](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/for.html#:~:text=the%20enhanced%20for%20to%20loop).
They enable you to easily iterate over the elements of a collection.

## For Loop Over Lists

{{EX1}}

## For Loop Over Sets

{{EX2}}

## For Loop Over Maps

Iterating over the elements of a map of type `{K: V}` using the for-loop construct will yield a loop variable whose type
is `tuple<K, V>`:

{{EX3}}

### _Note 1_:
<div class="warning">
For loops over tuples are not currently supported as it's unclear what the appropriate behavior would be iterating over
a collection of heterogeneous types. It's possible that in the future support may be added for a loop variable whose
type is oneof<...all unique types in the tuple...> but there are no current plans for prioritizing this.
</div>

### _Note 2_:
<div class="warning">
Possible use of an uninitialized variable is a compile-time error:
</div>

{{EX4}}

## Exiting a For Loop Early

You can exit a loop early by using the `break` keyword as below.

{{EX5}}

## Skipping to the Next Iteration of the For Loop

You can also skip ahead to the loop's next iteration by using the 'continue' keyword as below.

{{EX6}}