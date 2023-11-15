# Build Time vs. Runtime Configurability

This program demonstrates the two broad categories of configurability that a Claro program can take advantage of. For
_build time_ configurability, you can take advantage of Bazel's `select()` over `config_setting()`s to configure the
use of different files within the definition of arbitrary build targets, i.e. a `claro_module()` definition.

In this example, you'll be able to configure Bazel to build a `dev/prod/runtime` program based on a single flag defined
at `//examples/claro_programs/modules/demo_configurability/messaging:compile_time_env`.

You can see the usage of this flag for the definition of the `claro_module()` target defined at
`//examples/claro_programs/modules/demo_configurability/messaging:messaging`. This target chooses its `srcs`
at build time based on the config setting determined by the aforementioned flag. The following commands can be used to
build as dev/prod/runtime:

### Dev

```commandline
$ bazel build //examples/claro_programs/modules/demo_configurability --//examples/claro_programs/modules/demo_configurability/messaging:compile_time_env=dev
```

### Prod

```commandline
$ bazel build //examples/claro_programs/modules/demo_configurability --//examples/claro_programs/modules/demo_configurability/messaging:compile_time_env=prod
```

### Runtime

```commandline
$ bazel build //examples/claro_programs/modules/demo_configurability --//examples/claro_programs/modules/demo_configurability/messaging:compile_time_env=runtime
```

In the case of choosing the `Runtime` build configuration, the compiled Claro program will start up and defer to the
runtime flag `env` defined at
`//examples/claro_programs/modules/demo_configurability/messaging/messaging.claro_module_api` to determine the
message it should print. You can observe this using the following command to build as `runtime` and then configure the
message:

```commandline
$ bazel run //examples/claro_programs/modules/demo_configurability --//examples/claro_programs/modules/demo_configurability/messaging:compile_time_env=runtime -- --env World
```

which will yield the following output:

```commandline
Hello, World!
```