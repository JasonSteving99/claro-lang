# Implementing a Contract

Simply defining a contract is not sufficient to actually be useful, however, since the definition itself doesn't I
provide any logic. So, to actually *use* a Contract, we must implement it for a certain (set of) concrete type(s).

```
implement Operators<int> {
    function add(lhs: int, rhs: int) -> int {
        return lhs + rhs;
    }
}

implement Operators<string> {
    function add(lhs: string, rhs: string) -> string {
        return "{lhs}{rhs}";
    }
}
```

Now that you have implementations, you can either call them directly:

```
Operators::add(10, 20); # 30
Operators::add("Hello, ", "world"); # "Hello, world"
```

or even more valuably, you can also call the generic `sum` function over concrete types `int` or `string` because the
requirements are met for both!

```
requires(Operators<T>)
function sum<T>(l: [T]) -> T {...}

print(sum([1, 2, 3])); # 6 
print(sum(["a", "bc", "d"])); # abcd
```

In this way, Claro's Contracts interact with Generics to create a powerful form of code reuse where custom behavior can
be uniquely dictated by type information.