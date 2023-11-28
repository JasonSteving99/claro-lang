load("//src/java/com/claro:claro_build_rules_internal.bzl", "claro_module", "claro_module_internal")

visibility(["//stdlib/..."])

def claro_abstract_module(**kwargs):
    args = dict(kwargs, claro_module_impl = claro_module)
    return _claro_abstract_module_impl(**args)

def claro_abstract_module_internal(**kwargs):
    args = dict(kwargs, claro_module_impl = claro_module_internal)
    return _claro_abstract_module_impl(**args)

# Claro is fundamentally opposed to the proliferation of Classes and so they are not encoded into the language directly.
# However, Claro is also fundamentally advocating for flexible extensibility at the BUILD level. So as a demonstration,
# I'll extend the Claro ecosystem to support some notion of "Classes" that can expose certain capabilities that Class
# systems from other languages provide while not having to touch the compiler internals itself. I'm doing this to show
# that any Claro user could choose to get into the metaprogramming weeds to express a wide range of patterns themselves.
def _claro_abstract_module_impl(
        class_name,
        module_api_file,
        parameterized_type_names = [],
        default_srcs = [],
        overridable_srcs = {},
        default_deps = {},
        default_exports = [],
        claro_module_impl = claro_module
):
    # Codegen the aliases needed for type params of this module.
    module_parameterized_typedefs = []
    for t in parameterized_type_names:
        module_parameterized_typedefs.append('alias {0} : {{{0}}}'.format(t))
    module_parameterized_typedefs = '\n'.join(module_parameterized_typedefs)

    # Define the custom class function that can now be used to declare modules of this class.
    def _claro_class(
            name,
            type_params = {},
            override = {},
            srcs = [],
            deps = {},
            exports = [],
            visibility = [],
            debug = False
    ):
        # Validate args.
        if not _has_all_keys(type_params, parameterized_type_names):
            _logErr(
                msg = "Invalid type_params!",
                found = type_params,
                expected = {
                    "{0}".format(t) : "..." if t not in type_params else type_params[t]
                    for t in parameterized_type_names
                })
        if len(default_srcs) == 0 and len(overridable_srcs) == 0 and len(srcs) == 0:
            # There would be literally no srcs passed into claro_module() at this point. Error.
            fail("Missing required `srcs` list!")
        duplicated_deps = {}
        dep_labels = {Label(dep): dep for dep in deps.values()}
        default_dep_labels = {Label(dep): name for name, dep in default_deps.items()}
        for dep in dep_labels:
            if dep in default_dep_labels:
                # Don't use the canonicalized version of the label in the warning, use the string the user typed so it's
                # more clear to them what they should delete.
                duplicated_deps[default_dep_labels[dep]] = dep_labels[dep]
        if len(duplicated_deps) > 0:
            fail("""
Found unnecessary deps that are already default deps of the abstract class `{class_name}`:
{duplicated}
\tRemove these unnecessary deps and reference them in your srcs using the names given above.""".format(
                    class_name = class_name,
                    duplicated = "\n".join(["\t- {0}: {1}".format(name, dep) for name, dep in duplicated_deps.items()])))

        label_prefix = "{class_name}_{impl}".format(class_name = class_name, impl = name)

        # Generate an api file that has the type aliases for Key and Value types.
        native.genrule(
            name = label_prefix + "_api",
            srcs = [module_api_file],
            outs = [label_prefix + ".claro_module_api"],
            cmd = """
                _typedefs='{0}' && echo $$_typedefs $$(cat $(SRCS)) > $(OUTS)
            """.format(module_parameterized_typedefs.format(**type_params))
        )
        claro_module_impl(
            name = name,
            module_api_file = label_prefix + ".claro_module_api",
            srcs = default_srcs + [
                default_src if name not in override else override[name]
                for name, default_src in overridable_srcs.items()
            ] + srcs,
            deps = dict(default_deps, **deps),
            exports = default_exports + exports,
            visibility = visibility,
            debug = debug,
        )
    return _claro_class

def _logErr(msg = "Error!", found = "...", expected = "..."):
    fail("""
{err}
\tFound:
\t\t{found}
\tExpected:
\t\t{expected}
    """.format(
        err = msg, found = found, expected = expected))

def _has_all_keys(d, expected_keys):
    for k in d.keys():
        if k not in expected_keys:
            return False
    for k in expected_keys:
        if k not in d:
            return False
    return True


