# If-Else

```
var x: boolean = getBoolFromUser();
var s: string; # Declared but uninitialized. 
if (x) { # Curly braces are not optional.
    s = "blue";
else if (...) {
    s = "green";
} else {
    s = "red";
}
print(s); # Prints "blue", "green", or "red".
```

The above example is valid, but would become a compilation error if you removed one of the branches, because `s` might
not have a value when you go to print it:

```
var x: boolean = ...;
var s: string;
if (x) {
    s = "blue";
} else if (...) {
    s = "green";
}
print(s); #Error: Use of uninitialized var.
```