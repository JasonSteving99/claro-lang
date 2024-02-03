# Summary

[Welcome to Claro!](./chapter_1.md)

---

# User Guide 

- [Hello, World](./chapter_1/hello_world.generated_docs.md)
- [Getting Started!](./getting_started/getting_started.md)
    - [Understanding the Starter Project](./getting_started/understanding_starter_project/understanding_starter_project.generated_docs.md)
    - [Your First Program](./getting_started/first_program/first_program.generated_docs.md)
    - [Intro to Modules](./getting_started/intro_to_modules/intro_to_modules.generated_docs.md)

---

# Reference Guide

- [Common Programming Concepts](./common_programming_concepts.md)
    - [Variables & Primitive Types](./common_programming_concepts/variables_and_types/variables_and_types.generated_docs.md)
    - [Separate Variable Declaration & Initialization](./common_programming_concepts/sep_var_decl_and_init/sep_var_decl_and_init.generated_docs.md)
    - [Variable Reassignment](./common_programming_concepts/var_reassignment/var_reassignment.generated_docs.md)
    - [String Formatting](./common_programming_concepts/string_formatting/string_formatting.generated_docs.md)
    - [Control Flow](./control_flow.md)
        - [If-Else](./control_flow/if_else/if_else.generated_docs.md)
        - [While](./control_flow/while/while.generated_docs.md)
        - [For](./control_flow/for/for.generated_docs.md)
        - [Repeat](./control_flow/repeat/repeat.generated_docs.md)
        - [Pipes](./control_flow/pipes/pipes.generated_docs.md)
        - [Pattern Matching](./control_flow/match/match.generated_docs.md)
            - [Matching Structured Data](./control_flow/match/structed_data/structured_data.generated_docs.md)
            - [Wildcard Patterns](./control_flow/match/wildcards/wildcards.generated_docs.md)
            - [Static Exhaustiveness Checks](./control_flow/match/exhaustiveness_checks/exhaustiveness_checks.generated_docs.md)
        - [Collection Comprehension](./control_flow/collection_comprehension/collection_comprehension.generated_docs.md)
            - [Comprehension is More Than Syntax Sugar](./control_flow/collection_comprehension/more_than_syntax_sugar/more_than_syntax_sugar.generated_docs.md)
- [Types](./static_typing/static_typing.generated_docs.md)
    - [Builtin Collections](./static_typing/builtin_colls/builtin_collections.generated_docs.md)
        - [Lists](./static_typing/builtin_colls/list_type/list_type.generated_docs.md)
        - [Sets](./static_typing/builtin_colls/set_type/set_type.generated_docs.md)
        - [Maps](./static_typing/builtin_colls/map_type/map_type.generated_docs.md)
        - [Tuples](./static_typing/builtin_colls/tuple_type/tuple_type.generated_docs.md)
        - [Structs](./static_typing/builtin_colls/struct_type/struct_type.generated_docs.md)
    - [Oneofs](./static_typing/oneofs/oneofs.generated_docs.md)
        - ["Narrowing" / Type Guards](./static_typing/oneofs/narrowing/narrowing.generated_docs.md)
    - [Atoms](./static_typing/atoms/atoms.generated_docs.md)
    - [Aliases](./static_typing/aliases/aliases.generated_docs.md)
        - [Aliases are *Not* a New Type Declaration](./static_typing/aliases/not_a_new_type_decl/not_a_new_type_decl.generated_docs.md)
    - [User Defined Types](./static_typing/user_defined_types/user_defined_types.generated_docs.md)
        - [Parameterized Types](./static_typing/user_defined_types/parameterized_types/parameterized_types.generated_docs.md)
            - [Concrete Type Inference](./static_typing/user_defined_types/parameterized_types/concrete_type_inference/concrete_type_inference.generated_docs.md)
        - [Recursive Types](./static_typing/user_defined_types/recursive_types/recursive_types.generated_docs.md)
            - [Impossible Recursive Types](./static_typing/user_defined_types/recursive_types/impossible_recursive_types/impossible_recursive_types.generated_docs.md)
- [Type Inference](./type_inference/type_inference.generated_docs.md)
    - [Required Type Annotations](./type_inference/required_type_annotations/required_type_annotations.generated_docs.md)
- [Procedures](./procedures.md)
    - [Functions](./procedures/functions/functions.generated_docs.md)
    - [Consumers](./procedures/consumers/consumers.generated_docs.md)
    - [Providers](./procedures/providers/providers.generated_docs.md)
- [Lambdas & First Class Procedures](./lambdas_and_first_class_procedures/lambdas_and_first_class_procedures.generated_docs.md)
    - [Lambdas are Restricted "Closures"](./lambdas_and_first_class_procedures/lambda_closures/lambda_closures.generated_docs.md)
- [Error Handling](./error_handling/error_handling.generated_docs.md)
    - [Error Propagation](./error_handling/error_propagation/error_propagation.generated_docs.md)
- [Generics](./generics.generated_docs.md)
    - [Contracts](./generics/contracts/contracts.generated_docs.md)
        - [Implementing a Contract](./generics/contracts/implementing_contracts/implementing_contracts.generated_docs.md)
        - [Multiple Type Params](./generics/contracts/multiple_type_params/multiple_type_params.generated_docs.md)
            - [(Advanced) Implied Types](./generics/contracts/multiple_type_params/implied_types/implied_types.generated_docs.md)
        - [Dynamic Dispatch](./generics/contracts/dynamic_dispatch/dynamic_dispatch.generated_docs.md)
    - [Generic Return Type Inference](./generics/generic_return_type_inference/generic_return_type_inference.generated_docs.md)
- [Copying Data](./copying/copying.generated_docs.md)
    - [Mutability Coercion on Copy](./copying/mutability_coercion/mutability_coercion.generated_docs.md)
    - [Performance Optimizations](./copying/performance/performance.generated_docs.md)
    - [Known Copy Bugs](./copying/known_bugs/known_bugs.generated_docs.md)
- [Module System](./module_system/module_system.generated_docs.md)
- [Concurrency](./concurrency.md)
    - [Graph Procedures](./graph_procedures/graph_procedures.generated_docs.md)
    - [Graph Procedure Composition](./graph_procedures/graph_procedure_composition/graph_procedure_composition.generated_docs.md)
    - [Calling Graph Procedures](./graph_procedures/calling_graph_procedures/calling_graph_procedures.generated_docs.md)
    - [(Advanced) Conditional Subgraph Execution](./graph_procedures/conditional_subgraph_execution/conditional_subgraph_execution.generated_docs.md)
- [Fearless Concurrency](./fearless_concurrency/fearless_concurrency.md)
    - [Guaranteed Data-Race-Free Concurrency](./guaranteed_data_race_free/guaranteed_data_race_free.generated_docs.md)
    - [Guaranteed Deadlock-Free Concurrency](./guaranteed_deadlock_free.md)
        - [Blocking Procedures](./guaranteed_deadlock_free/blocking_procedures/blocking_procedures.generated_docs.md)
        - [Re: "What Color is Your Function?"](./guaranteed_deadlock_free/re_what_color_is_your_function.generated_docs.md)
        - [(Advanced) Blocking Generics](./guaranteed_deadlock_free/re_what_color_is_your_function/blocking_generics.generated_docs.md)

---

# StdLib

- [Default Modules](./stdlib/default_modules/default_modules.generated_docs.md)
{{stdlib_toc}}