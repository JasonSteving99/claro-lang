# Calculator REPL

This backend to the Calculator compiler is going to serve as a REPL
wrapping the interpreted compiler backend. Basically it'll operate
at a level just above the interpreter itself in order to maintain a
symbol table and heap between statement evaluations. 