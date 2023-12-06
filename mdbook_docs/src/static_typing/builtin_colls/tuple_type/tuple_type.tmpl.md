# Tuple

Tuples are a fixed-order, fixed-size collection of values which do not all have to be of the same type.

```
{{EX1}}
```

You can see in the example above, tuples interact w/ type validation in an interesting way worth making note of. When
you index into a tuple, you should generally prefer to use a literal int constant. When you do, Claro can statically
determine the type of the value you're accessing at compile time, which allows cleaner, safer code. If your index value
is hidden behind some indirection, Claro can't know the type at compile-time and will require a runtime type cast (which
is slow & unsafe).