# Welcome to Claro!

<img id="claro-logo" width=300 src="./images/ClaroLogoFromArrivalHeptapodOfferWeapon-transparentBackground.png">

## Claro is a statically typed JVM language that provides a well-lit path to building simple, highly concurrent, and scalable applications.

## Dependency Management Done Right
---
Claro was designed with modern build tooling in mind:
- Swap any dependency without changing a single line of source code
- Runtime "Dependency Injection" frameworks are a thing of the past
- First class [Module system](module_system/module_system.generated_docs.md)
## Fearless Concurrency
---
Developed by a Xoogler taking inspiration from years of hands-on development experience with Google-internal backend web
frameworks, Claro moves well beyond async/await. Claro’s declarative, DAG-based structured concurrency model provides an
easy-to-use abstraction to statically guarantee that programs are:
- Non-blocking
- [Data-race free](./guaranteed_data_race_free/guaranteed_data_race_free.generated_docs.md)
- [Deadlock free](./guaranteed_deadlock_free.md)
- [Optimally scheduled](./graph_procedures/graph_procedures.generated_docs.md)
- Scalable by default
## Data Oriented
---
- Strict separation between data and functionality
- [Mutability](static_typing/builtin_colls/builtin_collections.generated_docs.md#mutability) tracked at the type system level
- Extremely flexible built in algebraic data types
- Model arbitrary data structures with [zero boilerplate](static_typing/builtin_colls/builtin_collections.generated_docs.md#ad-hoc-declarations)
## Unapologetically Practical
---
- [Bi-directional type inference](type_inference/type_inference.generated_docs.md)
- Robust [standard library](stdlib/default_modules/default_modules.generated_docs.md)
- Builtin external package manager
- [Build time metaprogramming](metaprogramming/metaprogramming.generated_docs.md)
## Designed to Scale with You
---
- Incremental compilation out of the box
- Code reuse made simple
- Develop codebases of any size without devolving into a spaghetti labyrinth
## Actively In Development
---


<div class="warning">

**Claro is in active development and is not yet ready for production use**. While Claro has been in active development 
for more than 3 years, its design and implementation has been done entirely by its single maintainer, 
<a href="https://www.linkedin.com/in/jasonsteving/" target="_blank">Jason Steving</a> - while the foundation has been 
laid, contributions are welcome! Anyone with interest is actively encouraged to reach out and get involved!
</div>