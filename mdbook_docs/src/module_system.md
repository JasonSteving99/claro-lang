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

```
# example.claro_module_api

contract Numeric<T> {
  function add(lhs: T, rhs: T) -> T;
  function multiply(lhs: T, rhs: T) -> T;
}

newtype Foo : int
implement Numeric<Foo>;

consumer prettyPrint(lhs: Foo);
```

### Sources

An API alone simply defines an interface that the module will satisfy in its implementation sources. So implementations
must be provided in the form of one or more .claro files. The above API could be satisfied by the below implementation
files (note: this could be done in a single source file, but here it's split into multiple just as an example):

```
# contract_impl.claro

implement Numeric<Foo> {
  function add(lhs: Foo, rhs: Foo) -> Foo {
    return Foo(unwrap(lhs) + unwrap(rhs));
  }
  function multiply(lhs: Foo, rhs: Foo) -> Foo {
    return Foo(unwrap(lhs) * unwrap(rhs));
  }
}
```

```
# pretty_print.claro

consumer prettyPrint(f: Foo) {
  unwrap(f)
    |> "Foo: {^}"
    |> Boxes::wrapInBox(^)  # <-- Calling dep Module function.
    |> print(^);
}
```

### Dependencies

While Modules are intended to be consumed as a reusable component, it may also itself depend on other modules in order
to implement its public API. 

Notice that the implementation of `prettyPrint` above makes a call to `Boxes::wrapInBox(...)`. This is an example of
calling a procedure from a downstream dep Module in Claro. In order to build, this Module must place a dep on some 
Module that has *at least* the following signature in its API: `function wrapInBox(s: string) -> string;`. As you'll see
below, this Module will *choose* to give that downstream dependency Module the name `Boxes`, but any other name could've
been chosen.

## Defining BUILD Target

A Claro Module is fully defined from the above pieces by adding a `claro_module(...)` definition to the corresponding 
Bazel BUILD file:

```python
# BUILD

load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")

claro_module(
    name = "example",
    module_api_file = "example.claro_module_api",
    srcs = [
        "contract_impl.claro",
        "pretty_print.claro",
    ],
    deps = {
        "Boxes": ":box",  # <-- Notice the name "Boxes" is chosen by the consumer.
    },
    # This Module can be consumed by anyone.
    visibility = ["//visibility:public"],
)

claro_module(
    name = "box",
    module_api_file = "boxes.claro_module_api",
    srcs = ["boxes.claro"],
    # No visibility declared means that this Module is private to this Bazel package.
)

...
```

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

```
# test.claro

var f1 = Ex::Foo(1);
var f2 = Ex::Foo(2);

var addRes = Ex::Numeric::add(f1, f2);
Ex::prettyPrint(addRes);

var mulRes = Ex::Numeric::multiply(f2, Ex::Foo(5));
Ex::prettyPrint(mulRes);
```

```python
# BUILD

load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")

...

claro_binary(
    name = "test",
    main_file = "test.claro",
    deps = {
        "Ex": ":example",
    },
)
```

## Example Code

Complete source code for the examples above can be found 
[here](https://github.com/JasonSteving99/claro-lang/tree/main/mdbook_docs/src/module_system/examples) 