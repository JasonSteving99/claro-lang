# Generics

Oftentimes, you'll find that some code patterns keep coming up over and over and you'll want to find same way to factor
out the major commonalities in logic from the minor specific details that you'd want to just plug in as needed. For
example, you might realize that you're writing loops to filter lists based on conditions all over your code; the only
difference between the implementation in any of these occurrances of filtering being the element types and the specific
condition. But because you want to filter lists of all kinds of types you might not immediately think you could write a
single function that could be called wherever filtering is needed. __Enter Generics!__

```
function filter<T>(l: [T], pred: function<T -> boolean>) -> [T] {
    var res: [T] = [];
    var i = 0;
    while (i < len(l)) {
        if (pred(l[i])) {
            append(res, l[i]);
        }
        ++i;
    }
    return res;
}
```

The function `filter<T>(...)` is defined to take a list of elements of some arbitrary (generic) type, `T`, and a "
predicate" (single arg func returning a boolean) that takes in values of that generic type, `T`. In this example, the
particular type `T` is "unconstrained". The only constraint is the typical type behavior that for whatever values are
passed as args, the list elems must be of the same type as the input to the given predicate function.

So, that generic type can take on the "concrete" type of whatever data happens to be passed into the function at the
call site:

```
filter([1, 90, 10, 40], x -> x > 15); # [90, 40]
filter([[0], [0,0], [0, 0]], l -> len(l) > 1); # [[0, 0], [0, 0]]
```