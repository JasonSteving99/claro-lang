load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")


jflex(
  name = "lcalc_jflex",
  srcs = ["lcalc.flex"],
  outputs = ["Lexer.java"],
)

cup(
  name = "ycalc_cup",
  src = "ycalc.cup",
  symbols = "Symbol",
)

java_binary(
  name = "calculator",
  srcs = [":ycalc_cup"],
)

# java_import(
#   name = "jflex",
#   jars = [
#     "@jflex//jflex:jflex_bin",
#   ],
# )
# 
# java_library(
#   name = "lcalc_flex",
#   deps = [
#     ":jflex",
#   ],
#   srcs = [
#     ":lcalc.flex",
#   ],
# )
