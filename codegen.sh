#!/bin/bash

# (JW) so code generators are not my favorite thing but apparently lots of people like them, especially for onboarding. so here we go.

usage="bash codegen.sh <path> <name>"

if [ "$1" = "" ] || [ "$2" = "" ]; then
  echo "$usage"
  exit 1
fi

if [ ! -d "$1" ]; then
  echo "dir \"$1\" does not exist"
fi

dest="$1/$2"
mkdir -p "$dest"

if [ -f "$dest/BUILD" ]; then
  echo "BUILD file already exists"
  exit 1
fi

# BUILD
echo "load(\"@claro-lang//:rules.bzl\", \"claro_binary\")

claro_binary(
  name = \"$2\",
  main_file = \"$2.claro\",
  resources = {
    \"Input\": \"input.txt\",
  }
)" > "$dest/BUILD"

# input.txt
echo "look ma, no hands!" > "$dest/input.txt"

# claro file
echo "var file = resources::Input;
var data = files::read(file);

if (data instanceof string) {
  var message = strings::toUpperCase(data);
  print(message);
}" > "$dest/$2.claro"

# README.md
echo "bazel build //$dest:$2
bazel run //$dest:$2" > "$dest/README.md"

cat "$dest/README.md"
