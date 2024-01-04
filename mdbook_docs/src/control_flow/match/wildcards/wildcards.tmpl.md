# Wildcard Patterns

<div class="warning">
Note: Pattern Matching support is currently only partway through it's planned feature support (and has some open bugs to
be addressed). More to come!
</div>

The utility of Pattern Matching is dramatically increased by making use of wildcards. For example, they enable the below
match with cases that only specify partial matches and then **bind** matched values to a variable:

{{EX1}}

The syntax `W:int` is a "wildcard binding" that matches any `int` value and declares a variable that the `int` will be 
assigned to in the `case` code block.

## Case Ordering and Wildcards

Cases are semantically matched in the order that they appear in the source code. This means that it's possible to define
unreachable cases if the cases above already cover the pattern:

_Note: Claro's error messaging is a work in progress - the below error message will be improved._
{{EX2}}

Simply changing the ordering of the cases above will fix this problem:

{{EX3}}