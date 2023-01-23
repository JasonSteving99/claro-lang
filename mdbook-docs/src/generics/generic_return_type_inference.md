# Generic Return Type Inference

One very interesting capability that you get from the combination of Claro's bidirectional type inference and generics
is the ability to infer which Contract impl to defer to based on the expected/requested retorn type at a generic
function callsite. Let's get more specific.

```
contract Index<T, R> {
    function get(l: [T], ind: int) -> R;
}

implement Index<[int], int> {
    function get(l: [int], ind: int) -> int {
        return l[ind];
    }
}

implement Index<[int], tuple<boolean, int>> {
    function get(l: [int], ind: int) -> tuple<boolean, int> {
        if (ind < len(l) and ind >= 0) {
            return (true, l[ind]);
        }
        return (false, -1);
    }
}
```

For the above implementations of `Index<T, R>`, you'll notice that each function, `Index::get`, only differs in its
return type but not in the arg type. So, Claro must determine which impl to defer to by way of the contextually expected
return type. This, I believe leads to some very convenient ergonomics for configurability.

```
alias Result: tuple<boolean, int>

var l = [1,2,3];
var unsafeRes: int = Index::get(1, 10); # out of bounds runtime err.
var safeRes: Result = Index::get(1, 10); # (false, -1)
var ambiguous = Index::get(1, 10); # Compiler error, ambiguity
```