# Control Flow

Claro has only a few control flow structures at the current moment. These structures allow programs to execute code
both conditionally and repeatedly. The only thing to keep an eye on, coming from a dynamic language like Python, is that
Claro will statically validate that do not misuse conditional execution to run code that may attempt to use a variable
before initialization. The examples in the following sections will also demonstrate invalid code that Claro throw a
compile-time error on.