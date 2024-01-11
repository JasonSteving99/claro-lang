# Tuples

Tuples are a fixed-order, fixed-size collection of values which do not all have to be of the same type.

# Compile-Time Validated Indexing

You can see in the example below, tuples interact w/ type validation in an interesting way worth making note of. When
you index into a tuple, you should generally prefer to use a literal int constant. When you do, Claro can statically
determine the type of the value you're accessing at compile time, which allows safer and more efficient code.

For example, Claro is able to statically find the bug in the below program:

{{EX1}}

Making it easy to apply the fix:

{{EX2}}

# Runtime Validated Indexing

If your index value is hidden behind some indirection, Claro can't know the type at compile-time and will require a 
runtime type cast (which is slow & opens the door to runtime Panics if the actual type doesn't match the asserted type).

Claro will warn you at compile-time when this is necessary:

{{EX3}}

Fix the error by explicitly asserting the expected type by casting:

{{EX4}}

# Mutable Tuples

Unlike some other languages with tuple support, Claro imposes no arbitrary restriction that all tuples must necessarily 
be immutable. Just like any other builtin collection type, a Claro tuple may be declared mutable using the `mut` 
keyword when declaring a variable or initializing the value. You may then update element values at will as long as the 
initial type declaration for each element is honored.

Claro won't allow you to mutate a tuple that isn't explicitly declared mutable:

{{EX5}}

The mutation is legal when the tuple is explicitly annotated `mut`:

{{EX6}}
