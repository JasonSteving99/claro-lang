#!/usr/bin/env bash

# This script serves the purpose of hiding users from the fact that building a Claro program actually involves several
# steps of interaction with various Java Jars. This script goes through all the backflips on behalf of the user so that
# in the end there's a *single* artifact produced that users can directly invoke, namely a single fat executable Jar
# that has already had all Java deps bundled into it.

CLARO_COMPILER_JAR_NAME="claro_compiler_binary_deploy.jar"
CLARO_BUILTIN_DEPS_JAR_NAME="claro_builtin_java_deps_deploy.jar"

function main() {
  # First, validate that all the args actually are as expected.
  validateArgs "$@"

  IFS='.' read -r -a CLARO_MAIN_FILE_PARTS <<< "$1" # somehow this means to split the parts of the file foo.claro into (foo . claro)
  CLARO_MAIN_FILE=${CLARO_MAIN_FILE_PARTS[0]}
  CLARO_FILE_NAMES=$(echo "" "$@" | sed 's/ / --src /g')
  echo "Main file: $CLARO_MAIN_FILE"
  echo "Files: $CLARO_FILE_NAMES"

  # Move into a tempdir so that everything can be cleaned up in the end. The only artifact I want this script to leave
  # behind is literally just the fat executable Jar containing the compiled Claro program.
  ORIG_DIR=$(pwd)
  tempfoo=$(basename "$0")
  TMP_DIR=$(mktemp -d -t "${tempfoo}") || exit 1
  echo "Created tempdir: $TMP_DIR"
  cp "$@" "$TMP_DIR"
  cd "$TMP_DIR" || die

  # TODO(steving) I need to figure out better way to consistently identify this file's location on various machines...
  # Check if CLARO_COMPILER_PATH is defined
  if CLARO_COMPILER_PATH=$(check_env_var_and_set_default "\$CLARO_COMPILER_PATH" "$CLARO_COMPILER_PATH" $CLARO_COMPILER_JAR_NAME);
  then
    # Continue with the rest of the script
    echo "\$CLARO_COMPILER_PATH is set to $CLARO_COMPILER_PATH"
  else
    echo "$CLARO_COMPILER_PATH" >&2 && die
  fi

  # TODO(steving) I need to figure out better way to consistently identify this file's location on various machines...
  # Check if CLARO_BUILTIN_DEPS_PATH is defined
  if CLARO_BUILTIN_DEPS_PATH=$(check_env_var_and_set_default "\$CLARO_BUILTIN_DEPS_PATH" "$CLARO_BUILTIN_DEPS_PATH" $CLARO_BUILTIN_DEPS_JAR_NAME);
  then
    # Continue with the rest of the script
    echo "\$CLARO_BUILTIN_DEPS_PATH is set to $CLARO_BUILTIN_DEPS_PATH"
  else
    echo "Error: $CLARO_BUILTIN_DEPS_PATH" >&2  && die
  fi

  # I've intentionally packed the stdlib .claro_internal srcs into the claro_builtin_java_deps_deploy.jar so I can
  # extract them from the Jar to work with here.
  jar -xf "$CLARO_BUILTIN_DEPS_PATH" com/claro/stdlib/claro/
  # Dynamically lookup the .claro_internal files found packed in the jar so I don't need to keep updating this script.
  CLARO_STDLIB_SRCS=$(find com -type f -name "*.claro_internal")
  CLARO_STDLIB_SRCS=$(echo -n " $CLARO_STDLIB_SRCS" | tr '\n' ' ' | sed 's/ / --src /g')
  # Dynamically lookup the .claro_module files found packed in the jar so I don't need to keep updating this script.
  CLARO_STDLIB_MODULES=$(find com -type f -name "*.claro_module")
  CLARO_STDLIB_MODULES=$(
    echo -n "$CLARO_STDLIB_MODULES" |
    tr ' ' '\n' |
    # Who woulda thunk that awk is actually kinda sick. Weird as all hell... but kinda sick.
    awk '{split($0,parts,"."); n=split(parts[1],modname,"/"); print " --dep " modname[n] ":" parts[1] "." parts[2] " --stdlib_dep " modname[n]; }' |
    tr '\n' ' '
  )

  echo "Compiling $CLARO_MAIN_FILE.claro..."
  java -jar "$CLARO_COMPILER_PATH" --java_source --silent=true --classname="$CLARO_MAIN_FILE" --package=com.claro $CLARO_STDLIB_SRCS $CLARO_STDLIB_MODULES $CLARO_FILE_NAMES > "$CLARO_MAIN_FILE".java || die

  echo "Compiling $CLARO_MAIN_FILE.java..."
  javac -cp ".:$CLARO_BUILTIN_DEPS_PATH" "$CLARO_MAIN_FILE".java -d .

  # Here, I'm creating a "fat jar" executable so that there's a single executable artifact produced by this script
  # which you should be able to take anywhere that Java is installed and run it on its own.
  echo "Generating Executable $CLARO_MAIN_FILE.jar..."
  cp "$CLARO_BUILTIN_DEPS_PATH" "$CLARO_MAIN_FILE".jar
  jar -uf "$CLARO_MAIN_FILE".jar com/claro/*.class
  # I need to overwrite the entrypoint of the jarfile so that it starts at the codegen'd java for the user's Claro program.
  jar -ufe "$CLARO_MAIN_FILE".jar com.claro."$CLARO_MAIN_FILE"
  # This executable Jar doesn't need the stdlib sources anymore. Remove them. Remember that Jars are just zip files.
  zip -d "$CLARO_MAIN_FILE".jar "com/claro/stdlib/claro/*" > /dev/null || true

  echo "Moving executable Jar out of tmpdir."
  mv "$CLARO_MAIN_FILE".jar "$ORIG_DIR"

  echo "Cleaning up tmpdir."
  cd "$ORIG_DIR" || die
  rm -r "$TMP_DIR"

  echo "Done. Run your program using: \"claro $CLARO_MAIN_FILE\""
}

function validateArgs() {
  # Make sure there were actually some args passed.
  if [ $# -eq 0 ]; then
    echo "claroc requires at least one .claro file to compile. Try rerunning like the following:" >&2
    echo "  $ ./claro foo.claro [...]" >&2
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

function check_env_var_and_set_default() {
  # Get the name of the environment variable
  local env_var_val=$2
  # Get the name of the file to look for
  local file_name=$3

  # Check if the environment variable is defined
  if [ -z "$env_var_val" ]; then
    # Check if the file exists in the current directory
    if [ -f "$ORIG_DIR/$file_name" ]; then
      # Set the environment variable to the path of the file
      env_var_val="$ORIG_DIR/$file_name"
    else
      # Exit because the environment variable is not defined and the file does not exist
      echo "The environment variable $1 is not defined and the file $file_name does not exist. Exiting." >&2
      exit 1
    fi
  fi
  # Return the value of the environment variable
  echo "$env_var_val"
  exit 0
}


function die() {
  # Make sure to still cleanup the tmpdir
  if [[ -z "$TMP_DIR" ]]; then
    echo "Error: Cleaning up tmpdir."
    rm -r "$TMP_DIR"
  fi
  exit 1
}


# Extract script's location and convert to absolute path
CLARO_BIN_PATH=$(readlink -f "$(dirname "$0")")/../lib/claro/
# If compiler path is not defined, use the above (bin path) to make the compiler path
CLARO_COMPILER_PATH=${CLARO_COMPILER_PATH:=$CLARO_BIN_PATH/$CLARO_COMPILER_JAR_NAME}
# If deps path is not defined, use the above (bin path) to make the deps path
CLARO_BUILTIN_DEPS_PATH=${CLARO_BUILTIN_DEPS_PATH:=$CLARO_BIN_PATH/$CLARO_BUILTIN_DEPS_JAR_NAME}
# Assumption: JAR files and script are located in directories as below:
#   ..
#   |-- bin
#       |-- claro.sh
#       |-- claroc.sh
#   |-- lib
#       |-- claro
#           |-- claro_builtin_java_deps_deploy.jar
#           |-- claro_compiler_binary_deploy.jar
# My recommendation is that you place them somewhere like /usr/local/bin and /usr/local/lib/claro (assuming that the dir
# /usr/local/bin is already in your PATH).
# Alternatively, if you want to get fancier, you can use symlinks and the below structure.
#   ..
#   |-- bin
#       |-- claro -> ../lib/claro/claro.sh
#       |-- claroc -> ../lib/claro/claroc.sh
#   |-- lib
#       |-- claro
#           |-- claro.sh
#           |-- claroc.sh
#           |-- claro_builtin_java_deps_deploy.jar
#           |-- claro_compiler_binary_deploy.jar
# This gives the added benefit of your usage of the CLIs looking like the below:
#   $ claroc foo.claro
#   $ claro foo


main "$@"; die
