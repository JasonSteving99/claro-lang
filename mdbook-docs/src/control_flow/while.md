# While Loops

```
var : int = 0;
while (i < 10) {
    print (i++);
}
```

Possible use of uninitialized var is a compile-time error

```
var s: int;
while (...) {
    s = ...;
}
print(s); #Error
```

(__Note__: At the moment Claro has no builtin mechanisms for breaking out of a loop early or skipping ahead to the next
iteration. You'll have to do this manually which is really annoying for now.)
