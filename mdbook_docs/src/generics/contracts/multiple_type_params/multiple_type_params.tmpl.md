# Contracts with Multiple Type Params

So far we've only demonstrated very simple contracts with over a single generic type parameter - however, there is no
hard restriction on the number of type parameters that a contract can reference. (Of course, typical software design 
principles still apply, a contract with many type params is probably going to be too complex to actually be used 
effectively in practice.)  

Here's a contract defined over multiple type params:

{{EX1}}

And an implementation of that contract:

{{EX2}}

<div class="warning">

**Note**: Learn more about the `?=` operator used in the above example in the 
[Error Handling](../../../error_handling/error_handling.generated_docs.md) section.
</div>

## Calling a Contract Procedure Over Multiple Type Params

A contract procedure is always called in exactly the same way regardless of how many type parameters the contract was
defined over. 

{{EX3}}

## Limitation of the Above Contract Definition

<div class="warning">

Notice that in the [prior example](#calling-a-contract-procedure-over-multiple-type-params), the call to 
`RandomAccess::read(...)` is wrapped in an explicit static `cast(...)`. If you read closely, you can see that this is 
because the arguments alone **do not fully constrain the type that the call should return** (it could be that you intend
to dispatch to some other impl `RandomAccess<Node<string>, Foo>`). Read more about this situation in
[Required Type Annotations](../../../type_inference/required_type_annotations/required_type_annotations.generated_docs.md).
</div>

By allowing this sort of contract definition, Claro actually opens up a design space for contracts that can have 
multiple slight variations implemented, enabling callers can conveniently just get the return type that they need based
on context. 

However, you could argue that this particular contract definition does not benefit from that flexibility. This contract
would arguably be more useful if `RandomAccess::read(...)` didn't have an ambiguous return type.

**Learn how to address this issue using ["Implied Types"](./implied_types/implied_types.generated_docs.md)**
