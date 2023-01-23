# Tuple

Tuples are a fixed-order collection of types which do not all have to be the same. Whereas a List is an arbitrarily
sized collection of values of the same type, a tuple always has a fixed size.

```
var myPair: tuple<int, string> = (1, "one");

# Claro will interprate literal int index at compile-time for type validation
var myInt: int = myPair[0]; 
var myStr: string = myPair[1]; 

# Claro requires a type cast for non-literal index.
var index:int = ...;
myInt = myPair[index]; # Compile Error
myInt = (int) myPair[index]; # OK, opting into runtime type validation.
```

you can see in the example above, tuples interact w/ type validation in an interesting way worth making. note of When
you index into a tuple, you should generally prefer to use a literal int constant. When you do, Claro can statically
determine the type of the value you're accessing at compile time, which allows cleaner, safe code. If your index is
behind some indirection, Claro can't know the type at comp the and will require a runtime type cast (slow & unsafe).