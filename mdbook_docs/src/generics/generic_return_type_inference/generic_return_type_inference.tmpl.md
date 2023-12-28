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