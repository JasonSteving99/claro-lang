# Inference Examples

Instead of:

```
var i: int = 1;
var b: boolean = true;
var l: [tuple<int, boolean>] = [(1, true), (2, false)];
```

You could write:

```
var i = 1;
var b = true;
var l = [(1, true), (2, false)];
```

Each corresponding statement has exactly the same meaning. They differ only syntactically. Each variable is still
declared to have teh same static type you'd expect.