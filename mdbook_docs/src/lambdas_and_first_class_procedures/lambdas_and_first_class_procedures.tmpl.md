# Lambdas & First Class Procedures

Claro opens you up to taking full advantage of functional programming techniques by allowing you to assign Procedures to
variables and to pass them around as data, allowing you to hand them off to be called later. 

## Defining Lambdas

Lambdas expressions look something like the examples below.

{{EX1}}

<div class="warning">

_**Note**: lambdas [require explicit type annotations](../type_inference/required_type_annotations/required_type_annotations.generated_docs.md#lambda-expressions-assigned-to-variables)
, but Claro does support an alternative syntax sugar to bake the type annotation directly into the lambda expression:_
</div>

{{EX2}}

## First Class Procedure References

You may also reference named procedures as first-class data just like lambdas:

{{EX3}}