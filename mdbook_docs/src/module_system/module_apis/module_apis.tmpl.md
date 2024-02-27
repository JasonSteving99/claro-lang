# Module APIs

As you've seen in the previous section, a Claro Module is defined by its API which fully declares what downstream
consumers of the Module will gain access to by placing a dependency on it. In Claro, this API is explicitly declared 
using a `.claro_module_api` file that simply contains type information, signatures, and names of what's implemented 
within, but does not contain any concrete implementations itself. This may seem like it's just extra boilerplate, but in
fact, this separation of API and implementation is actually the source of the extreme modularity that Claro programs can
leverage. 

In particular, it's very important to note that this separation implies that **it is impossible for two separate modules 
to be** 
<a href="https://www.wikiwand.com/en/Coupling_(computer_programming)#introduction" target="_blank">"tightly coupled"</a>!
Whereas in other programming languages like Java or Python, you must consciously plan ahead in order to maintain 
<a href="https://www.wikiwand.com/en/Loose_coupling" target="_blank">"loose coupling"</a> between program components.

This guarantee of loose coupling between Modules will be evaluated in more detail in a 
[following section](../swapping_deps/swapping_deps.generated_docs.md), but for now, we'll just take a moment to 
explicitly outline exactly what can be exported by a Module's API.