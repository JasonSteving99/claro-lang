# While Loops

{{EX1}}

<div class="warning">
Possible use of an uninitialized variable is a compile-time error:
</div>

---
```
var s: int;
while (...) {
    s = ...;
}
print(s); #Error
```
---

## Exiting a While Loop Early

You can exit a loop early by using the `break` keyword as below.

{{EX2}}

## Skipping to the Next Iteration of the While Loop

You can also skip ahead to the loop's next iteration by using the 'continue' keyword as below.

{{EX3}}