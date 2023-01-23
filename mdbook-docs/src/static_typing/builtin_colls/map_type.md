# Maps

A mapping of keys of a fixed value type, to values of a fixed type.

```
var myMap: {string: int} = {};
myMap["Jason"] = 28; 
print("Jason" in myMap); # true
myMap["Kenny"] = 29;
print(myMap); # {"Jason":28, "Kenny":29}
```

(Note: for now maps are also missing many useful operations that should be builtin. Will be built as part of stilib
later.) 
