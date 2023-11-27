package com.claro.compiler_backends.java_source.monomorphization;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

public class DepModuleMonomorphizationCLIOptions extends OptionsBase {
  @Option(
      name = "coordinator_port",
      abbrev = 'p',
      help = "Port used for communicating back to the coordinator compiler instance.",
      defaultValue = "-1"
  )
  public int coordinatorPort;

  // TODO(steving) TESTING! DELETE THIS, The uniqe name should be looked up in the .claro_module.
  @Option(
      name = "dep_module_unique_name",
      abbrev = 'n',
      help = "The unique name of the dep module to handle.",
      defaultValue = ""
  )
  public String depModuleUniqueName;

  @Option(
      name = "dep_module_file_path",
      abbrev = 'd',
      help = "The path to the dep module's .claro_module file.",
      defaultValue = ""
  )
  public String depModuleFilePath;
}
