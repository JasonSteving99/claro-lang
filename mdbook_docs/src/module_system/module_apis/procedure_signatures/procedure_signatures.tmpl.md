# Procedure Signatures

Of course, the most obvious reason to create a new Module is to factor out some logic for the sake of making it reusable
in multiple places in your program, or even just to keep another Module's implementation from growing in size
indefinitely. So, the first thing that you're likely to export from a Module are procedures. To do so, you will simply
declare the signature of the procedure(s) to be exported - that is, everything but the implementation of the procedure.
A procedure signature ends with a `;` instead of the usual implementation logic inside curly braces.

For example, the following signatures are exported from the StdLib's 
[strings module](../../../stdlib/strings_module.generated_docs.md):

{{EX1}}

<div class="warning">

Including a procedure signature in a Module's API file is a declaration that any dependent of this Module will have 
access to a procedure with the given signature, so Claro will statically validate that any `claro_module(...)` target
exporting any procedure signatures **actually** implements that procedure within its given `srcs`.
</div>

So, your build target will be required to declare which `.claro` source file(s) actually _implement_ the exported
procedures as explained in the introduction to [defining Modules](../../module_system.generated_docs.md#sources).