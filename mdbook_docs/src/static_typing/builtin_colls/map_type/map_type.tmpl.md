# Maps

A mapping of keys of a fixed value type, to values of a fixed type.

{{EX1}}

## Checking if a Key Exists

You can check for the existence of a key in a map by using the `in` keyword.

{{EX2}}

## Iterating Over the Entries of a Map

Claro's `for` loop supports iterating over the entries of a map, with each entry modeled as `tuple<K, V>`:

{{EX3}}

## Stdlib `maps` Module

A large variety of map operations are available in the
[stdlib's `maps` module](https://github.com/JasonSteving99/claro-lang/tree/main/stdlib/maps). For example, you can 
declare a default value that will be used as fallback if the read key doesn't exist in the map by using the following
function declared in the `maps.claro_module_api` file:

{{EX4}}

{{EX5}}