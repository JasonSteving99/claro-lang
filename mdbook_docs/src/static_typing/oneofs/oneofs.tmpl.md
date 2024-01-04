# Oneofs

Something that's often left unaddressed by statically typed programming languages is the ability to model a value that
can take on one of an arbitrary set of types. Many other languages approximate this sort of ability through a notion of
"sub-typing" relationships between a hierarchy of types. While sub-typing as found broad use and much support throughout
the programming languages ecosystem, Claro has been designed under the belief that sub-typing leaves much to be desired
and opens the door to all sorts of unwanted and unnecessary complexity and leads to error-prone coding patterns. So,
on principle, Claro will never support sub-typing, and instead provides support for `oneof` types (also known as 
tagged-unions in other languages). 

{{EX1}}

## Check the Concrete Type of a Oneof With the `instanceof` Operator

The entire point of a `oneof` type is to be able to write branching logic on the concrete type that is _actually_
represented by the `oneof` at runtime. One way of achieving this is with the `instanceof` boolean operator that allows
you to check the concrete type at runtime:

{{EX2}}

<div class="warning">
It's somewhat nonsensical to do an instanceof check on any concrete type so Claro statically rejects that.
</div>

{{EX3}}