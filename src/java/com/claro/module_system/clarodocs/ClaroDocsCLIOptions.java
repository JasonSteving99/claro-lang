package com.claro.module_system.clarodocs;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

import java.util.List;

public class ClaroDocsCLIOptions extends OptionsBase {
  @Option(
      name = "module",
      help = "A list of string paths to the .claro_module files for which ClaroDocs will be generated.",
      allowMultiple = true,
      defaultValue = ""
  )
  public List<String> modules;
  @Option(
      name = "out",
      help = "The output file to write the generated ClaroDocs HTML to.",
      defaultValue = ""
  )
  public String out;
  @Option(
      name = "treejs",
      help = "Path to the required TreeJS JS source to reference in generated html.",
      defaultValue = ""
  )
  public String treejs;
  @Option(
      name = "treejs_css",
      help = "Path to the required TreeJS CSS source to reference in generated html.",
      defaultValue = ""
  )
  public String treejs_css;
}