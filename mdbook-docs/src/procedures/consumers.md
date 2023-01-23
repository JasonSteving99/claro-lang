# Consumers

A Procedure that takes in some data but doesn't return any data.

```
consumer dump(s: string, age: int, heightFt: int) {
    # String formatting.
    print("{s} is {age} years old and {heightFt}ish feet tall.");
}

# Calling the consumer. Syntactically, consumers are always used as statements,
# never as an expression (something that has a value).
dump("Laura", 28, 5); # Laura is 28 years old and 5ish feet tall.
```

Note: Consumers tend to be an inherent waste of computation time __unless__ that consumer does some side-effecting
operation observable outside the program scope. So, it may be a useful hint that if you're reading code that includes a
call to a consumer, some I/O is very likely taking place (if not, you should delete the call entirely as it's a waste of
work).