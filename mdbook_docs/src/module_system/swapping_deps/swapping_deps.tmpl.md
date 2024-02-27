# Swapping Dependencies

Claro's Module system was very carefully designed to guarantee that it's **statically impossible for two separate
modules to be
<a href="https://www.wikiwand.com/en/Coupling_(computer_programming)#introduction" target="_blank">"tightly coupled"</a>**.
In this section we'll dive into exactly what that means.

As you've already seen in previous sections, Claro Modules explicitly declare a public API that indicates the full set 
of procedures/values/Types that the Module's consumers will gain access to. Of course, some form of this is present in
every language. The unique distinction is that Claro Module dependencies can be directly swapped out to any other Module
with an appropriate API **without changing a single line of code in any `.claro` source files**. 

For example, the below API...

{{EX1}}

...could be implemented by multiple Modules...

{{EX2}}

...and then the **exact same `.claro` source code**...

{{EX3}}

...could be compiled against either Module...

{{EX4}}
{{EX5}}

...and the behavior would depend on which dependency was chosen...

{{EX6}}
{{EX7}}

## Dep Validity is Based on Usage

The other subtle point that's likely easy to miss if it's not pointed out explicitly is that the validity of a Module
dependency is completely dependent upon the _usage_ of the dependency. In less opaque terms, this just means that a
Module dependency is valid if the Module's API actually exports everything that is _used_ by the consuming code. The
consuming code doesn't make any constraints on _anything other than what it actually uses_. So, a dependency can be
swapped for another that actually exports a _completely different_ API, so long as it **_at least_** exports everything
that the consuming code _actually used_ from the original Module's API. 

For example, if a third Module actually implemented a totally different API such as:

{{EX8}}

the dependency would _still_ be valid because `example.claro` only actually _uses_ the `getMessage(...)` procedure that
is exported by both `:look_ma` and `:hello_world`. 

This single design decision actually enables a huge amount of Build time configurability options. If you'd like to see
more about how you can take advantage of this, read about how you can swap dependencies programmatically using 
[Build flags](../../metaprogramming/swapping_deps/swapping_deps.generated_docs.md). And if you're interested in becoming
a power-user, this feature can be leveraged in some very powerful ways via
[Build Time Metaprogramming](../../metaprogramming/metaprogramming.generated_docs.md).