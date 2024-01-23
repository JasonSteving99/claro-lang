# (Advanced) Implied Types

In the previous section we 
[noticed a problem](../multiple_type_params.generated_docs.md#limitation-of-the-above-contract-definition)
with the definition of the contract:

{{EX1}}

Specifically, we decided that this contract definition is too unconstrained: knowing the types of the arguments in a
`RandomAccess::read(...)` call is insufficient to know which contract implementation the call should dispatch to.

To drive this point home, in the below example there are **two implementations of the contract** both over the same
collection type, but over different element types. In this definition of the contract, there's nothing stopping this
from happening.

{{EX2}}

As a result, any calls to the `RandomAccess::read(...)` function are inherently ambiguous, and require the return type
to be explicitly, statically constrained. Any unconstrained calls to this contract procedure would result in a 
compilation error where Claro tries to ask the user which contract implementation they actually intend to dispatch to:

{{EX3}}

<div class="warning">

**Note**: This ambiguity is an inherent feature of the `RandomAccess<C, E>` definition itself. **Claro would still
produce a compilation error if there _happened_ to only be a single implementation** because another conflicting
implementation could be added at any time.
</div>

## Statically Preventing Ambiguous Contract Definitions with Implied Types

Of course, there's arguably very little reason for this particular contract to **actually** allow multiple 
implementations over the same collection type (the second implementation `RandomAccess<Node<string>, int>` above is very
contrived). So ideally this contract definition should statically encode a restriction on such implementations. It
should only be possible to implement this contract **once** for a given collection type - meaning that there would be
no more ambiguity on the return type of calls to `RandomAccess::read(...)`.

Thankfully, you can encode this restriction directly into contract definition using "Implied Types":

{{EX4}}

The **only** change is in the declaration of the contract's generic type parameters: `<C => E>` (read: "C implies E") 
was used instead of `<C, E>`. This explicitly declares to Claro that this implication **must be maintained** for all
types, `C`, over which the contract is implemented throughout the entire program. 

As a result, it will now be a compilation error for two separate implementations `RandomAccess<C, E1>` and 
`RandomAccess<C, E2>` _(where `E1 != E2`)_ to coexist, as this would violate the constraint that `C => E`.

So now, attempting to define the two implementations given in the [previous example](#fig-2) would result in a 
compilation error:

{{EX5}}

Now, by eliminating one of the implementations you fix the compilation error. In addition, **you're now able to call
`RandomAccess::read(...)` without any ambiguity!**

{{EX6}}

## Deciding Whether to Constrain a Contract's Type Params is a Judgement Call

If you made it through this entire section, you should have a strong understanding of the purpose and value add of 
implied types. However, keep in mind that **both unconstrained and implied types have their uses!**

Don't just assume that every contract should be defined using implied types. You should be applying good design
judgement to determine if and when to use this feature or to leave a contract's type parameters unconstrained.