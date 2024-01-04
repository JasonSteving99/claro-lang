# "Narrowing" / Type Guards

Inspired by TypeScript's support for this, when you perform an `instanceof` check on a variable with a `oneof` type
within a conditional statement, Claro automatically "narrows" the type of the variable to the checked type. This is
logically valid because the only way that control-flow could possibly reach that context is if that was actually the
type at runtime.

{{EX1}}

<div class="warning">
Note: Claro is not implementing full "flow typing" here. The type will be "widened" again to its originally declared
type if you assign a value of any type other than the narrowed type to a variable in a context where it's been narrowed.
</div>

{{EX2}}

## Non-Trivial Example Usage

For a less trivial example of working with `oneof` types, the below function is able to pretty-print a linked list by
checking if the current node is the end of the list or not by branching on the type of the `next` reference:

{{EX3}}

_The above example relies on concepts described in later sections, so consider checking out
[User Defined Types](../user_defined_types/user_defined_types.generated_docs.md) and [Generics](../../generics.generated_docs.md)
for some more info._
