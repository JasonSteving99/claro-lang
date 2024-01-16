# Collection Comprehension

Claro takes direct inspiration from Python's powerful "Comprehensions" syntax to provide powerful single-expression
operation to encode the common pattern of iterating some collection's elements and putting them into a new collection
after potentially filtering and transforming the elements. This allows you to do complex initializations of both mutable
and immutable collections directly in-line without having to drop down to multi-line initialization using some form of
loop. 

## List Comprehension

Compare the following loop-based initialization:

{{EX1}}

with the List Comprehension based alternative:

{{EX2}}

As you can see, taking the time to get comfortable with Comprehension expressions can serve to significantly simplify
your code.

<div class="warning">

**Optional**: it may be useful to read a bit about the 
"<a href="https://www.wikiwand.com/en/Set-builder_notation" target="_blank">Set Builder Notation</a>" that inspires this
syntax in both Claro and Python.
</div>

## Set Comprehension

The same convenient Comprehension-based initialization is also supported for Sets. Simply swap the square brackets 
`[...]` for curly braces `{...}`:

{{EX3}}

Notice now, even though the same mapping and filtering is done over the same input collection as in the list
comprehension examples above, the output here does not duplicate the entry `"*"` as Set Comprehension honors set 
semantics. (However, to be very explicit, `strings::repeated("*", 1)` was called twice).

## Map Comprehension

And finally, Comprehension-based initialization is also supported for Maps. Again, use curly braces `{...}` instead of
square brackets `[...]`, but this time a colon-separated key-value entry is computed from the input collection instead
of a single value:

{{EX4}}

<div class="warning">

**Warning**: Map Comprehension will Panic at runtime if you attempt to create multiple entries yielding the same key.
It's still up for debate whether this is desirable behavior - it's possible that this may be updated to some other model
such as "last entry wins". TBD.
</div>
