package com.claro.compiler_backends.java_source;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

import java.util.List;

public class JavaSourceCompilerBackendCLIOptions extends OptionsBase {
  @Option(
      name = "silent",
      abbrev = 's',
      help = "Compiler will omit debug output.",
      defaultValue = "false"
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

  @Option(
      name = "dep",
      help = "A string in the format '<module_name>:<claro_module_file_path>' representing the binding of a concrete " +
             "Module dependency directly depended upon by the .claro srcs in this claro_binary() or claro_module().",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> deps;

  @Option(
      name = "transitive_exported_dep_module",
      help =
          "A path to a .claro_module file that is declared 'exported' by one of the direct dep modules in order to " +
          "provide the definition of any types that are referenced in the dep module's .claro_module_api file.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> transitive_deps;

  @Option(
      name = "export",
      help = "Exported transitive dep modules. Modules are listed here in order to ensure that consumers of this " +
             "module actually have access to the definitions of types defined in this module's deps.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> exports;

  @Option(
      name = "stdlib_dep",
      help = "Module deps that are treated as privileged stdlib modules for the purpose of deciding whether or not " +
             "using the module is required, or whether it must be exported if referenced in the .claro_module_api file",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> stdlib_modules;

  @Option(
      name = "output_file_path",
      help = "The path to the output file to put the generated Java.",
      defaultValue = ""
  )
  public String output_file_path;
}
