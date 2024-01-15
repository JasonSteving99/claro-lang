# Generic Return Type Inference

One very interesting capability that you get from the combination of Claro's bidirectional type inference and generics
is the ability to infer which Contract implementation to defer to based on the expected/requested return type at a
procedure call-site. Let's get more specific.

{{EX1}}

For the above implementations of `Index<T, R>`, you'll notice that each function, `Index::get`, only differs in its
return type but not in the arg types. So, Claro must determine which implementation to defer to by way of the
contextually expected return type. This, I believe leads to some very convenient ergonomics for configurability, though
the onus for "appropriate" use of this feature is a design decision given to developers.

{{EX2}}

## Ambiguous Calls

As described in further detail in the section on 
[Required Type Annotations](../../type_inference/required_type_annotations/required_type_annotations.generated_docs.md),
certain generic procedures that return a value of a generic type may require the call to be explicitly constrained by
context. In particular, this will be the case when the generic type does not appear in any of the procedure's declared
arguments. 

For example, calling the above `Index::get` Contract Procedure will statically require the "requested" return type to be
statically constrained by context:

{{EX3}}

### Ambiguity via Indirect Calls to Contracts

Note that while this specific ambiguity can only possibly arise as a result of calls to a Contract procedure, even 
indirect calls can cause this problem:

{{EX4}}

Again, you can resolve this issue by explicitly declaring the "requested" return type:

{{EX5}}