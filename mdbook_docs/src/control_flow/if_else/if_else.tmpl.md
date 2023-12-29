# If-Else

{{EX1}}

<div class="warning">
Possible use of an uninitialized variable is a compile-time error:
</div>

#### _Fig 2:_
---
```
var rng = random::forSeed(1);
var r = random::nextNonNegativeBoundedInt(rng, 100);

var s: string;
if (r < 33) {
    s = "red";
} else if (r < 66) {
    s = "green";
} 
# `s` is uninitialized if r >= 66.

print(s); # Error: `s` may not have been initialized.
```
---
