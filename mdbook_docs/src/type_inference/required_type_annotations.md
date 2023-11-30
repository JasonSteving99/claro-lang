# Required Type Annotations

Some of this will be skipping ahead to more advanced topics that haven't been brought up yet, so come back to this part
later if you want to, but just takeaway the fact that there are same limited situations where Claro will require a type
annotation to understand your intent. Note that these situations are not just a limitation of the compiler, even if
Claro would somehow choose a type for you in these situations, your colleagues (or your future self) would struggle to
comprehend what type was being inferred.

For clarity and correctness in the following situations, you will be required to write an explicit type annotation:

1. Function / Consumer args.
2. Lambda Expressions assigned to variables.
3. Non-literal Tuple Subscript - in the form of a runtime type cast.
4. Function/Provider call for a Generic return type - when the generic return type can't be inferred from arg(s) of the
   same generic type.
5. Any of the above Expressions when passed to a generic function arg position.
