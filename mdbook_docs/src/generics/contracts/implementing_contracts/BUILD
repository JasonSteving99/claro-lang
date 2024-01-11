load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "implementing_contracts",
    doc_template = "implementing_contracts.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/generics/contracts/implementing_contracts:ex_impls.claro",
            "hidden_setup": "//mdbook_docs/src/generics/contracts:ex_contract.claro",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/generics/contracts/implementing_contracts:ex2.claro",
            "hidden_setup": [
                "//mdbook_docs/src/generics/contracts:ex_contract.claro",
                "//mdbook_docs/src/generics/contracts/implementing_contracts:ex_impls.claro",
            ],
        },
        {
            "example": "//mdbook_docs/src/generics/contracts/implementing_contracts:ex3.claro",
            "hidden_setup": [
                "//mdbook_docs/src/generics/contracts:ex_contract.claro",
                "//mdbook_docs/src/generics/contracts/implementing_contracts:ex_impls.claro",
                "//mdbook_docs/src/generics/contracts:ex2.claro",
            ],
        },
    ],
)
