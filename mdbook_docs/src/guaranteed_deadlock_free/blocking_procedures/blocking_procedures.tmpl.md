# Blocking Procedures

Whereas other languages with some form of builtin concurrency mechanism may tend to make it harder to write async code
than blocking code, Claro is very intentional about inverting that balance. Make the good things easy and the bad things
hard. So, you may write blacking code in Claro, but as it's really only intended to be used in limited contexts, Claro
forces your hand. Any procedure that makes use of the `<-|` operator either directly or indirectly, must be explicitly
annotated to be `blocking`:

{{EX1}}

## Graph Procedures May not Call any Blocking Procedures (Directly or Indirectly)

To prevent deadlocking, procedures annotated `blocking` may not be called from a Graph: 

{{EX2}}

Therefore, you can be confident that the threading implementation of any logic defined within a Graph Procedure will 
certainly not suffer from liveliness issues in the form of deadlocks (of course, you may still write code with bugs such
as infinite loops that may lead to a "livelock").