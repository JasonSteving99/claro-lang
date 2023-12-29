# Pipes

Piping is a control flow mechanism that is not common to all languages, but is truly just syntactic sugar (one of the
few pure-sugar features in the language). Piping gives you a mechanism to decompose deeply nested function calls into a
linear chain of operations that happen one after the other much like any other imperative code you're familiar with. The
main thing to know is that on each line beginning with the "pipe" operator `|>`, the token `^` (known as the
"backreference" operator) refers to the value of the expression before the pipe operator. It is intended that the `^`
operator, visually resembles an arrow pointing upwards to the value produced on the line above.

{{EX1}}

Compare to the alternative code without piping. Notice how use of piping in the above example even allows elimination of
multiple temporary variables - this is a powerful motivator for using pipelining as it's well known that [naming is one
of the two hard problems in computer science](https://martinfowler.com/bliki/TwoHardThings.html):

{{EX2}}

## Textually Linear Data Flow

It's worth noting that the primary motivation for pipelining support in Claro is to take what could otherwise be highly
nested procedure calls whose data flow conceptually runs "inside-out", and allow it to instead be written in a style
that has the data flowing in the same linear direction as the textual source code itself. 

As such, Claro's pipelines introduce a concept of "source" and "sink". The "source" is the initial expression (data) 
that conceptually "enters the pipeline" and the "sink" is some terminal statement that consumes the data that "comes out
of the end of the pipeline". This means that the pipeline's sink can be any valid Claro statement that uses the value
computed by the penultimate step in the pipeline.

Notice how the following variable assignment allows data to flow top-to-bottom in the same direction as the source code
is written textually:

{{EX3}}

whereas, very confusingly, the non-pipelining style has source code written top-to-bottom, but yet the data is 
effectively flowing in a circuitous route from bottom-to-top and then back down again.

{{EX4}}

This may be something that we all get used to in other languages, but it's certainly an obstacle to readability 
particularly for new programmers.