# Aliases

Aliases are a powerful feature that allow the expression of arbitrary types. In their simplest form, they may be used as
syntactic sugar to reduce keystrokes and cognitive overhead from typing out a full type literal.

```
# You can imagine typing this out is verbose/annoying.
alias IntsTo2TupleFn: function<|int, int| -> tuple<int, int>>

var swapped: IntsTo2TupleFn = lambda (a, b) -> (b, a);
print("swapped(1, 2) -> {swapped(1, 2)}"); # swapped(1, 2) -> (2, 1)

var doubled: IntsTo2TupleFn = lambda (a, b) -> (2*a, 2*b);
print("doubled(1, 2) -> {doubled(1, 2)}"); # doubled(1, 2) -> (2, 4)
type(doubled); # function<|int, int| -> tuple<int, int>>

var ddd: [IntsTo2TupleFn] = [doubled];
type(ddd); # [function<|int, int| -> tuple<int, int>>]
```

### Aliases are *Not* Syntactic Sugar

To be absolutely clear, Aliases are *not* simply syntactic sugar as shown in the trivial example above. Without aliases
there would be no way to define a recursive data type in the language. Read on to the next sections to learn about
recursive alias type definitions.