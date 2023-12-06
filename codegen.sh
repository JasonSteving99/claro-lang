#!/bin/bash

# (JW) so code generators are not my favorite thing but apparently lots of people like them, especially for onboarding. so here we go.

usage="bash codegen.sh <path> <name>"

if [ "$1" = "" ] || [ "$2" = "" ]; then
  echo "$usage"
  exit 1
fi

#if [ ! -d "$1" ]; then
  #echo "dir \"$1\" does not exist"
#fi

mkdir -p "$1"

# BUILD
echo "load(\"@claro-lang//:rules.bzl\", \"claro_binary\")

claro_binary(
  name = \"$2\",
  main_file = \"$2.claro\",
  resources = {
    \"Input\": \"input.txt\",
  }
)" > "$1/BUILD"

# input.txt
echo "look ma, no hands!" > "$1/input.txt"

# claro file
echo "var file = resources::Input;
var data = files::read(file);" > "$1/$2.claro"

# README.md
echo "bazel build //$1/$2:$2
bazel run //$1/$2:$2"
