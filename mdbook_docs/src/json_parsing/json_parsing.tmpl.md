# JSON Parsing

Claro strives to make development easier in many ways, and as being able to write programs that interact with the 
network is an important goal, Claro has some initial support for automatically generating efficient JSON parsers for
relatively arbitrary formats. If you know the schema of the JSON data that you'll be interacting with, and can describe
it as some Claro struct, then in general you can automatically parse JSON data from a string directly into the Claro
type. 

Claro's JSON parsing is implemented by **generating a custom parser for the target data format at compile time**. So, in
addition to ergonomic improvements, this approach offers potential performance benefits over a general-purpose JSON
parser.

For example, the following JSON string could be included in a 
[Resource File](../resource_files/resource_files.generated_docs.md):

{{EX1}}

We can represent that JSON format as the following Claro data structure:

{{EX2}}

And now, the JSON string can be parsed by a simple call to the `fromJson(...)` builtin function:

{{EX3}}

## Limitations

<div class="warning">

To be clear, Claro's JSON parsing support is currently fairly constrained and doesn't yet support the full range of
possible JSON formats. You'll be warned at compile-time if the format you're attempting to auto-parse is supported or
not. More work will be needed to complete the implementation. **If you're interested in contributing to this please
reach out!**  
</div>