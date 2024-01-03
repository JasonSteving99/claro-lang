# Impossible-to-Initialize Recursive Alias Type Definitions

<div class="warning">
Some recursive type definitions are rejected at compile-time because they would be impossible to instantiate. 
</div>

The issue with these type definitions is that the type recursion has no implicit "bottom" and implies an infinitely 
nested value. Because it's impossible to ever initialize a value composed of infinitely many values (you'd never finish
typing the code), Claro lets you know right away at compile time that the infinitely recursive type is rejected for 
being unusable.

The below recursive type definitions all trigger compile-time warnings from Claro indicating that these types aren't 
usable and are therefore illegal.

{{EX1}}
