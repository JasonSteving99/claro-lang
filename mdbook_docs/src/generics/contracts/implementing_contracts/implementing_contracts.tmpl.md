# Implementing a Contract

Simply defining a contract is not sufficient to actually be useful, however, since the definition itself doesn't provide
any logic. So, to actually *use* a Contract, we must implement it for a certain (set of) concrete type(s):

{{EX1}}

Now that you have implementations, you can either call them directly:

{{EX2}}

Or, even more valuable, you can also call the generic `sum` function from the 
[previous section](../contracts.generated_docs.md) over concrete types `int` or `string` because the requirements are 
met for both!

{{EX3}}

In this way, Claro's Contracts interact with Generics to create a powerful form of code reuse where custom behavior can
be uniquely dictated by type information. And, unlike in an Object-Oriented language, this code reuse did not rely on
creating any subtyping relationships.

## A Note on Static Dispatch via "Monomorphization"

As a performance note - even beyond the conceptual simplification benefits of avoiding dependence on subtyping
relationships to achieve custom behaviors, Claro also achieves performance gains through its ability at compile-time to
statically *know* which custom Contract implementation will be called. In the Object-Oriented approach, generally
speaking the procedure receiving an arg of an interface type doesn't know which particular implementation will be called
at runtime. This leads to the situation where a runtime "dispatch table"/"vtable" lookup is required to determine which
particular implementation to call for each particular value passed into the procedure. Claro is a "monomorphizing"
compiler, meaning that during compilation each Generic Procedure has a customized implementation codegen'd for each set
of concrete types the procedure is *actually* called with. In this way, there's no runtime dispatch overhead when types
are statically known (which is __always__ true unless you're explicitly calling a generic procedure over a `oneof<...>`
type - but in this case you're consciously opting into dynamic dispatch overhead).