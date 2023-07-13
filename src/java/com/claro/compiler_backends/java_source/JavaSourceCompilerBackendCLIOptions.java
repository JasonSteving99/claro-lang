package com.claro.compiler_backends.java_source;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

import java.util.List;

public class JavaSourceCompilerBackendCLIOptions extends OptionsBase {
  @Option(
      name = "silent",
      abbrev = 's',
      help = "Compiler will omit debug output.",
      defaultValue = "true"
  )
  public boolean silent;

  @Option(
      name = "classname",
      abbrev = 'n',
      help = "The name to be given to the generated Java class.",
      defaultValue = ""
  )
  public String classname;

  @Option(
      name = "package",
      abbrev = 'p',
      help = "The package to be used for the generated Java class. Must be formatted as a valid Java package.",
      defaultValue = ""
  )
  public String java_package;

  @Option(
      name = "src",
      help = "A Claro source file to be compiled.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> srcs;

  @Option(
      name = "unique_module_name",
      help = "The globally unique name of this module.",
      defaultValue = ""
  )
  public String unique_module_name;
}
