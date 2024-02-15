# Resource Files

Strangely, bundling files into builds to be read at runtime regardless of where the executable program gets moved and
run from can be a significant pain point in many languages. Of course, each language provides some mechanism to address
this concern, but it typically requires either learning about some external build system feature, or figuring out how to
pass the correct sequence of flags to compilers. And then even once you do, figuring out the correct incantation to
successfully read that resource file can be just as frustrating 
(<a href="https://howtodoinjava.com/java/io/read-file-from-resources-folder/" target="_blank">looking at you Java</a>).

Claro tries to make this much simpler by directly modelling Resource Files as part of the exposed `claro_binary()` and
`claro_module()` build rules that you'll be using already. Declaring a Resource File to be bundled into the final deploy
Jar is as simple as listing it in your Build target similarly to how a Module dependency would be declared:

{{EX1}}

The Build target above has an explicit build time dependency on a Resource File named `example_resource.txt`. As you've
by now come to expect, if the file is missing for some reason Bazel will raise a Build error letting you know. You won't
simply have to try running your program and go through a whole debugging process just to find out 5 minutes later that
you misspelled the file name.

Now, your program has access to the Resource File `MyResource` by using the auto-generated `resources::MyResource` 
static value. It can then be read using one of the available functions exported by the StdLib's
[`files` Module](../stdlib/files_module.generated_docs.md):  

{{EX2}}