# Concrete Type Inference

Claro is able to automatically infer the type of `maybeStr` whenever it would be possible to do so. Generally speaking,
this is possible when the concrete type is actually referenced in the initialization.

{{EX1}}

<div class="warning">
It's not always possible to automatically infer the type of an instance of a parameterized type. In particular, the 
below example is impossible to automatically infer as the concrete type is not actually referenced in the 
initialization:
</div>

_Note: Claro's error messaging is a work in progress - the below error message will be improved._

{{EX2}}

In these situations Claro will require you to provide an explicit type annotation to disambiguate your intentions:

{{EX3}}