package claro.lang;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

public class TestGraphsParserCLIOptions extends OptionsBase {
  @Option(
      name = "claro_file",
      help = ".claro file to search for Graphs.",
      abbrev = 'f',
      defaultValue = ""
  )
  public String claro_file;
  @Option(
      name = "output_file",
      abbrev = 'o',
      help = "Output file.",
      defaultValue = ""
  )
  public String output_file;
}
