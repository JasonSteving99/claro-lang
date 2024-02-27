load("//antlr/graphs:graph_pngs.bzl", "render_graphs")

# Running this target will write test-graphs.md and test2-graphs.md
# to the current source dir. Then viewing those files with a
# Mermaid plugin in your IDE will render the graphs as diagrams.
render_graphs(
    name = "render_graphs",
    srcs = ["test.claro", "test2.claro"],
)