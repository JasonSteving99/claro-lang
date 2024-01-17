# Copying Data

In the course of writing software, it can be very common that you'll need to copy some data. In particular, when dealing
with collections that are either themselves mutable, or contain mutable elements, copying can be needed so that one copy
of the data can be updated while leaving the other unchanged.

However, you may find that many languages (e.g. Java/C++) make this extremely simple task prohibitively difficult 
requiring planning ahead to explicitly implement copying support on every data type that you think you'll want to copy 
in the future. To address this, Claro supports **deep copying** out-of-the-box with the builtin `copy(...)` function. 

{{EX1}}

## Deep Copying

Claro's builtin `copy(...)` function performs a **deep copy**, meaning that the entire nested structure is traversed
and copied (as needed). The below example copies some nested data and demonstrates that the resulting internal data can
be mutated in isolation:

{{EX2}}