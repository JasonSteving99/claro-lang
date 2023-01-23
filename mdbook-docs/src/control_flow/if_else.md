# If-Else

```
var x: boolean = getBoolFromUser(); #Could change each run.
var s: string; #Declared but uninitialized. 
if (x) { # Curly braces are not optional.
    s = "blue";
} else { # Could have also added an else if (..) {...}} block
    s = "red";
}
print(s); # Prints "blue" or "red".
```

The above example is valid, but would become a compilation error if you removed one of the branches, because `s` might
not have a value when you go to print it:

```
var x: boolean = ...;
var s: string;
if (x) {
    s = "blue";
}
print(s); #Error: Use of uninitialized var.
```