package(default_visibility = ["//src/java/com/claro/claro_programs/demo_server/buggy_buggies:__pkg__"])

# TODO(steving) This is only temporary. Long term, once Claro's Module system supports exporting newtype defs and
# TODO(steving)   initializers/unwrappers, then these .claro files should be properly exposed via modules.
exports_files(glob(["*.claro"]))

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
