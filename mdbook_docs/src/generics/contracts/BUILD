load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["*.claro"]), visibility = ["//mdbook_docs/src/generics/contracts/implementing_contracts:__pkg__"])

doc_with_validated_examples(
    name = "contracts",
    doc_template = "contracts.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/generics/contracts:ex1.claro",
            "hidden_setup": "//mdbook_docs/src/generics/contracts:ex_contract.claro",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/generics/contracts:ex_contract.claro",
            "append_output": False,
        },
    ],
)
