# Separate Variable Declaration & Initialization

The previous example demonstrates the simultaneous declaration and initialization of a new variable and its initial
value. It is also possible to delay initialization to happen independently of declaration.

```
var i: int;
i = 10;
```

(Note: this is particularly useful when you may want to initialize to different values in different branches of an
if-else chain for example.)
