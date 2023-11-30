# Generic Return Type Inference

One very interesting capability that you get from the combination of Claro's bidirectional type inference and generics
is the ability to infer which Contract implementation to defer to based on the expected/requested return type at a
procedure call-site. Let's get more specific.

```
contract Index<T, R> {
    function get(l: [T], ind: int) -> R;
}

implement Index<[int], int> {
    function get(l: [int], ind: int) -> int {
        return l[ind];
    }
}

alias SafeRes : tuple<boolean, int>

implement Index<[int], SafeRes> {
    function get(l: [int], ind: int) -> SafeRes {
        if (ind >= 0 and ind < len(l)) {
            return (true, l[ind]);
        }
        return (false, -1);
    }
}
```

For the above implementations of `Index<T, R>`, you'll notice that each function, `Index::get`, only differs in its
return type but not in the arg types. So, Claro must determine which implementation to defer to by way of the
contextually expected return type. This, I believe leads to some very convenient ergonomics for configurability, though
the onus for "appropriate" use of this feature is a design decision given to developers.

```
var l = [1,2,3];
var outOfBoundsInd = 10;
var unsafeRes: int = Index::get(l, outOfBoundsInd); # out of bounds runtime err.
var safeRes: SafeRes = Index::get(l, outOfBoundsInd); # (false, -1)
var ambiguous = Index::get(l, outOfBoundsInd); # Compiler error, ambiguous call to `Index::get`.
```