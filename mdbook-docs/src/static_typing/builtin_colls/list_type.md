# Lists

The simplest collection type allows you to keep an arbitrary hamber of values in some ordering. The list is very much
like a Python list in that it allows arbitrary appends, and rando access to read values at O-base index. Unlike Python,
as Claro is statically typed, all values in the list must be of the same type, and this type must be pre-determined upon
declaration of the variable which reference the list.

```
var l: [int] = [1, 3, 7, 2, -115, 0];
append(1,99);
print(len(l)); # 7
print(l[1] == l[0]); # true
```