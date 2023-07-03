#!/usr/bin/env bash

# This script serves the purpose of hiding users from the fact that building a Claro program actually involves several
# steps of interaction with various Java Jars. This script goes through all the backflips on behalf of the user so that
# in the end there's a *single* artifact produced that users can directly invoke, namely a single fat executable Jar
# that has already had all Java deps bundled into it.

function main() {
  # First, validate that all the args actually are as expected.
  validateArgs "$@"

  IFS='.' read -r -a CLARO_MAIN_FILE_PARTS <<< "$1" # somehow this means to split the parts of the file foo.claro into (foo . claro)
  CLARO_MAIN_FILE=${CLARO_MAIN_FILE_PARTS[0]}
  CLARO_FILE_NAMES=$(echo "$@" | tr ' ' ',')
  echo "Main file: $CLARO_MAIN_FILE"
  echo "Files: $CLARO_FILE_NAMES"

  # Move into a tempdir so that everything can be cleaned up in the end. The only artifact I want this script to leave
  # behind is literally just the fat executable Jar containing the compiled Claro program.
  ORIG_DIR=$(pwd)
  tempfoo=$(basename $0)
  TMP_DIR=$(mktemp -d -t "${tempfoo}") || exit 1
  echo "Created tempdir: $TMP_DIR"
  cp "$@" "$TMP_DIR"
  cd "$TMP_DIR" || die

  echo "Creating symlink to Claro Compiler Jar"
  CLARO_COMPILER_JAR_NAME="claro_compiler_binary_deploy.jar"
  # TODO(steving) I need to figure out how to consistently identify this file's location on various machines...
  CLARO_COMPILER_PATH="/private/var/tmp/_bazel_jasonsteving/efcd1bf992362b57bda2d1a8112007a7/execroot/claro-lang/bazel-out/host/bin/src/java/com/claro/claro_compiler_binary_deploy.jar"
  maybeCreateSymlink $CLARO_COMPILER_JAR_NAME $CLARO_COMPILER_PATH

  echo "Creating symlink to Claro Builtin Deps Jar"
  CLARO_BUILTIN_DEPS_JAR_NAME="claro_builtin_java_deps_deploy.jar"
  # TODO(steving) I need to figure out how to consistently identify this file's location on various machines...
  CLARO_BUILTIN_DEPS_PATH="/private/var/tmp/_bazel_jasonsteving/efcd1bf992362b57bda2d1a8112007a7/execroot/claro-lang/bazel-out/darwin-fastbuild/bin/src/java/com/claro/claro_programs/claro_builtin_java_deps_deploy.jar"
  maybeCreateSymlink $CLARO_BUILTIN_DEPS_JAR_NAME $CLARO_BUILTIN_DEPS_PATH

  # TODO(steving) I need to figure out how to consistently identify this file's location on various machines...
  CLARO_STDLIB_SRCS="/Users/jasonsteving/Claro/claro-lang/src/java/com/claro/stdlib/claro/builtin_functions.claro_internal,/Users/jasonsteving/Claro/claro-lang/src/java/com/claro/stdlib/claro/builtin_types.claro_internal"

  echo "Compiling $CLARO_MAIN_FILE.claro..."
  java -jar claro_compiler_binary_deploy.jar --java_source --silent=true --classname=$CLARO_MAIN_FILE --package=com.claro --srcs=$CLARO_STDLIB_SRCS,$CLARO_FILE_NAMES > $CLARO_MAIN_FILE.java || die

  echo "Compiling $CLARO_MAIN_FILE.java..."
  javac -cp ".:claro_builtin_java_deps_deploy.jar" "$CLARO_MAIN_FILE".java -d .

  # Here, I'm creating a "fat jar" executable so that there's a single executable artifact produced by this script
  # which you should be able to take anywhere that Java is installed and run it on its own.
  echo "Generating Executable $CLARO_MAIN_FILE.jar..."
  cp $CLARO_BUILTIN_DEPS_JAR_NAME "$CLARO_MAIN_FILE".jar
  jar -uf "$CLARO_MAIN_FILE".jar com/claro/*.class
  # I need to overwrite the entrypoint of the jarfile so that it starts at the codegen'd java for the user's Claro program.
  jar -ufe "$CLARO_MAIN_FILE".jar com.claro."$CLARO_MAIN_FILE"

  echo "Moving executable Jar out of tmpdir."
  mv "$CLARO_MAIN_FILE".jar "$ORIG_DIR"

  echo "Cleaning up tmpdir."
  cd "$ORIG_DIR" || die
  rm -r "$TMP_DIR"

  echo "Done. Run your program using: \"claroc.sh $CLARO_MAIN_FILE\""
}

function validateArgs() {
  # Make sure there were actually some args passed.
  if [ $# -eq 0 ]; then
    echo "claroc requires at least one .claro file to compile. Try rerunning like the following:"
    echo "  $ ./claroc.sh foo.claro [...]"
    exit 1
  fi

  # Get the list of arguments
  args=("$@")

  # Loop over the list of arguments and check if they end in ".claro"
  for arg in "${args[@]}" ; do
    # Check if the argument ends in ".claro"
    if [[ "$arg" != *".claro" ]]; then
      # The argument does not end in ".claro", so print an error message
      echo "The argument $arg does not end in \".claro\"."
      exit 1
    fi
  done
}

function maybeCreateSymlink() {
  symlink_name="$1"
  symlink_full_path="$2"
  # Check if the symlink already exists
  if [ ! -L "$symlink_name" ]; then
    # The symlink doesn't exist, so create it
    ln -s "$symlink_full_path" .
  else
    # The symlink already exists, so print a message
    echo "Symlink to $symlink_name already exists."
  fi
}

function die() {
  # Make sure to still cleanup the tmpdir
  rm -r "$TMP_DIR"
  exit 1
}

main "$@"; exit
