# Static Values

In addition to Type definitions and Procedure signatures, Modules are also able to export static (read: unchanging)
values. This seemingly simple feature actually addresses the core value add of heavyweight "Dependency Injection"
frameworks like Guice, Dagger, or Spring[^1] while providing the static compile-time validation that you'd expect of a
first-class programming language feature. 

The below Module API exports a struct containing a simple server config that's fixed (static) throughout the server's
lifecycle:

{{EX1}}

The value itself will be provided by implementing a `provider static_<static value name>() -> <static value type>`, for
example, the following provider implementation reads and parses[^2] the config values from a JSON resource[^3] file:

{{EX2}}

{{EX3}}

<div class="warning"><b>This syntax is very likely to change.</b> Expressing this via a <i>naming convention</i> is
extremely undesirable, so any suggestions for a more appropriate syntax are very welcome.</div>

And now, a downstream dependent of the Module exporting the `SERVER_CONFIG` static value can just directly use the value
as it was initialized at program startup by the given provider.

{{EX4}}

## Static Values Must be Deeply Immutable

The primary restriction placed on Static Values is that they **must be deeply immutable** to prevent static values from
being used in such a way could lead to data races. Because Static Values can be directly referenced _anywhere_ in your
program, this means they can be referenced directly or transitively by
[Graph Procedures](../../../graph_procedures/graph_procedures.generated_docs.md) or by Lambdas directly scheduled to
execute off the main thread using the StdLib's [futures module](../../../stdlib/futures_module.generated_docs.md). This
must be prevented in order to keep with Claro's philosophy of making it impossible for two threads to share mutable
state.

## Initialization Order

In general, Static Values are initialized on program startup[^4] before a single line of the "main file" (determined by 
`claro_binary(name = ..., main_file = ..., deps = ...)`) ever actually ran. To demonstrate this, let's add a 
`print(...)` statement to both the Static Value's provider, and to the main file that references it:

{{EX5}}

{{EX6}}

## "Lazy" Static Values

It's possible, however, that it might not be desirable for this sort of static initialization to happen eagerly like
this (for example if the value isn't guaranteed to even be used). So, Claro allows static values to optionally be
declared `lazy`:

{{EX7}}

which will effectively wrap every reference to the value in logic that will first check if the value still needs to be
initialized and the initialization logic will be performed exactly once the very first time a read of the Lazy Static 
Value is actually executed at runtime:

{{EX8}}

In the case of this example, lazy initialization could mean that the file read of the JSON config resource never
actually needs to occur if it would never actually be read. This is a fairly insignificant performance optimization, but
one that will be welcome to any developers that have become accustomed to this sort of capability being provided by more
heavyweight dependency injection frameworks.

## Static Value Providers May Depend on Other Static Values

Finally, it's worth explicitly noting that Static Value providers may depend on other Static Values, with the only
restriction being that circular dependencies between Static Value providers are forbidden. In fact, Claro will reject
them at compile time to ensure that you don't accidentally create an infinite loop during initialization.

---

[^1]: Claro doesn't support these DI frameworks' concept of "scopes" explicitly, but Claro's Static Values could be 
conceptually considered to be in the ["Singleton" scope](https://github.com/google/guice/wiki/Scopes#singleton) in any
of the mentioned DI frameworks.

[^2]: Learn more about Claro's support for automatic JSON Parsing. (TODO(steving) Add documentation on Claro's JSON Parser generation.)

[^3]: Learn more about Claro's support for Resource Files in the StdLib's [files module](../../../stdlib/files_module.generated_docs.md).

[^4]: To be very explicit, technically Static Values are instantiated the first time that the JVM's ClassLoader loads 
      the generated Class representing the Module exporting the Static Value. Hence the calls to `Config::log(...)` to
      make the example more compelling.
