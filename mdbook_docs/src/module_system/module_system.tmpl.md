# Module System

All but the most trivial programs will require some mechanism for decomposing a larger program into smaller, reusable 
components that can be composed into a larger whole. In Claro projects, this is accomplished via the Module System
whereby distinct functionality can be organized logically to facilitate encapsulation. In addition, Claro's Module 
System is the source of Claro's build incrementality - modules are compiled in isolation, allowing caching such that
modules do not need to be recompiled unless its own or its dependencies implementations have changed.

## Defining a Module

A Module exposes an API that is implemented by some set of source files which may depend on other modules.

### API

Module APIs are explicitly defined using a .claro_module_api file that will list exported procedure signatures, type
declarations, static values, and Contract implementations that are publicly exposed to consumers that place a dependency
on this module.

{{EX1}}

### Sources

An API alone simply defines an interface that the module will satisfy in its implementation sources. So implementations
must be provided in the form of one or more .claro files. The above API could be satisfied by the below implementation
files (note: this could be done in a single source file, but here it's split into multiple just as an example):

{{EX2}}

{{EX3}}

### Dependencies

While Modules are intended to be consumed as a reusable component, it may also itself depend on other modules in order
to implement its public API. 

Notice that the implementation of `prettyPrint` above makes a call to `Boxes::wrapInBox(...)`. This is an example of
calling a procedure from a downstream dep Module in Claro. In order to build, this Module must place a dep on some 
Module that has *at least* the following signature in its API: `function wrapInBox(s: string) -> string;`. As you'll see
below, this Module will *choose* to give that downstream dependency Module the name `Boxes`, but any other name could've
been chosen.

<div class="warning">

**Dependency Naming:** While consumers are allowed to pick any name they want for Modules that they depend on, it should
be noted that Claro will adopt the convention that all non-StdLib Module names **must begin with an uppercase letter**.
All StdLib Modules will be named beginning with a lowercase letter. This is intended to allow the set of StdLib modules
to expand over time without ever having to worry about naming collisions with user defined Modules in existing programs.

Static enforcement of this convention hasn't been implemented yet, but just know that it's coming in a future release. 
</div>

## Defining BUILD Target

A Claro Module is fully defined from the above pieces by adding a `claro_module(...)` definition to the corresponding 
Bazel BUILD file:

{{EX4}}

### Building a Module

In order to validate that a `claro_module(...)` target compiles successfully, you can run a Bazel command like the 
following:

(Assuming the BUILD file is located at //path/to/target)
```
$ bazel build //path/to/target:example
```

This will build the explicitly named target and its entire transitive closure of dependencies (assuming their build 
results have not been previously cached in which case they'll be skipped and the cached artifacts reused).

## Executable Using Above Example Module

To close the loop, the above example Module could be consumed and used in the following executable Claro program in the
following way.

{{EX5}}

{{EX6}}