# Providers

A Procedure that takes in no data but returns some data.

```
provider getInt() -> int {
    return 10;
}

# Calling a provider.
var myInt = getInt();
print(myInt); # 10
```