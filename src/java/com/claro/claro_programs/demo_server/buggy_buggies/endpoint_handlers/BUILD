load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

package(default_visibility = ["//src/java/com/claro/claro_programs/demo_server/buggy_buggies:__pkg__"])

claro_module(
    name = "endpoint_handlers",
    module_api_file = "endpoint_handlers.claro_module_api",
    srcs = [
        ":gen_game_page_handler", # Notice this is a Bazel-generated file. Macros anyone? :)
        "game_move_handler.claro",
        "get_best_moves_handler.claro",
        "start_new_game_handler.claro",
    ],
    deps = {
        "Agent": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_agent:buggy_agent",
        "BuggyBuggiesClient": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:buggy_buggies_client",
        "Utils": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/utils:utils",
        "Pos": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/data_structures:position",
    },
)

# All of this is obviously some seriously over-complicated nonsense, but I've intentionally done this as a demonstration
# of Claro's integration w/ Bazel being a great enabler for building dynamic code generation solutions in a safe and
# reproducible way. The awk scripts in these genrules were quite annoying to get working, but now that I've done it,
# this is fundamentally a detail that can be ignored.
genrule(
    name = "gen_game_page_handler",
    srcs = [
        "escaped_game_page_html.txt",
        "game_page_handler_template.claro"
    ],
    # Replace the placeholder "{GAME_PAGE_HTML}" with the contents of the file escaped_game_page.txt.
    cmd = "awk 'NR==FNR { a[n++]=$$0; next } /{GAME_PAGE_HTML}/ { for (i=0;i<n;++i) print a[i]; next }1' $(location escaped_game_page_html.txt) $(location game_page_handler_template.claro) > $(OUTS)",
    outs = ["game_page_handler.claro"],
)

genrule(
    name = "escaped_special_chars_game_page_html",
    srcs = ["game_page.html",],
    # Read in the html file and escape every '"' and '{' as '\"' and '\{' respectively. Then wrap every line as a Claro
    # string followed by a comma.
    cmd = "awk -v q=\"\\\"\" '{gsub(q, \"\\\\\\\"\"); gsub(\"{\", \"\\\\{\"); print \"    \\\"\" $$0 \"\\\\n\\\",\"}' $< > $(OUTS)",
    outs = ["escaped_game_page_html.txt"],
)
