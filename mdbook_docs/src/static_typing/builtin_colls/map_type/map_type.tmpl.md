# Maps

A mapping of keys of a fixed value type, to values of a fixed type.

{{EX1}}

## Checking if a Key Exists

You can check for the existence of a key in a map by using the `in` keyword.

{{EX2}}

## Stdlib `maps` Module

A large variety of map operations are available in the
[stdlib's `maps` module](https://github.com/JasonSteving99/claro-lang/tree/main/stdlib/maps). For example, you can 
declare a default value that will be used as fallback if the read key doesn't exist in the map by using the following
function declared in the `maps.claro_module_api` file:

```
# Returns the value to which the specified key is mapped, or `defaultValue` if this map contains no mapping for the key.
function getOrDefault<K,V>(m: {K:V}, k: K, defaultValue: V) -> V;
```

{{EX3}}