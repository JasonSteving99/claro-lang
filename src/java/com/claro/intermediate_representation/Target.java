package com.claro.intermediate_representation;

public enum Target {
  // This is used for literally converting source program to Java source.
  JAVA_SOURCE,
  // Interpret the program instructions within the CompilerBackend itself instead of producing a compiled output.
  INTERPRETED,
  // Same as the INTERPRETED target above, except that this is run in an interactive terminal environment. Simply
  // exists to configure some knobs on annoying things that have to be customized for the REPL experience to work.
  REPL,
}
