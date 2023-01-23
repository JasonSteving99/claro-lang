# (Advanced) Recursive Alias Type Definitions

A more advanced usage of type aliases includes using recursive self-reference(s) to the alias type in order to define
recursive types. This allows you to define self-similar nested structures of arbitrary (but finite) depth as in the
following example:

```
# TODO(steving) Rewrite this example w/ oneof<int, [IntOrList]> when supported.
alias IntOrList : tuple<boolean, int, [IntOrList]>

print("All of the following values satisfy the type definition for IntOrList:");
var myIntOrList: IntOrList;
myIntOrList = (true, 9, []);
print(myIntOrList); # (true, 9, [])

myIntOrList = (false, -1, []);
print(myIntOrList); # (false, -1, [])

myIntOrList = (false, -1,
  [
    (true, 2, []),
    (false, -1,
      [
        (false, -1, []),
        (true, 99, [])
      ]
    )
  ]
);
print(myIntOrList); # (false, -1, [(true, 2, []), (false, -1, [(false, -1, []), (true, 99, [])])])

append(myIntOrList[2], (true, 999, []));
print(myIntOrList); # (false, -1, [(true, 2, []), (false, -1, [(false, -1, []), (true, 99, [])]), (true, 999, [])])
```
