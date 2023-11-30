# Lambdas & First Class Procedures

Claro opens you up to taking full advantage of functional programming techniques by allowing you to assign Procedures to
variables and to pass them around as data, allowing you to hand them off to be called later. As such you can do the
following:

```
var f: function<int -> int> = x -> x + 1;
var c: consumer<int> = x -> { print(k); };
var p: provider<int> = () -> 10;
```

You may also reference defined procedures as data:

```
function add(x: int, y: int) -> int {...}

var biConsumer: consumer<int, int, function<|int, int| -> int>>
    = lambda (x, y, mapFn) -> {
        print(mapFn(x, y));
    };

# Pass a reference to the `add()` function as a first class arg.
biConsumer(10, 5, add); #15.
```