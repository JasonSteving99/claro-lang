# Aliases

Aliases are a powerful feature that allow the expression of arbitrary types. In their simplest form, they may be used as
syntactic sugar to reduce keystrokes and cognitive overhead from typing out a full type literal.

{{EX1}}

### Aliases are Syntactic Sugar

To be absolutely clear, Aliases are simply syntactic sugar as shown in the example above. They provide a mechanism for
reducing the amount of boilerplate code that may need to be written where full type annotations are explicitly required.
They also allow you to communicate some sort of "intent" where you would like to communicate the purpose of a value to
other developers (or your future self) without actually committing to defining a fully new custom type (though aliases 
should be used for this purpose with caution). For example, below you'll see an example of using aliases to indicate 
that different `int` values have different interpretations.

{{EX2}}

## Overuse of Aliases Can be a Code Smell

<div class="warning">
Keep in mind that excessive use of aliases can be a code smell. If you are using an alias to try to encode some semantic
distinction between values, it's very likely that you are writing highly bug-prone code as aliases do not provide any 
level of compile time verification that values of different alias types don't get accidentally conflated. 
</div>

{{EX3}}

See [User Defined Types](../user_defined_types/user_defined_types.generated_docs.md#compile-time-enforcement) for an example of how to 
address this issue.