package com.claro.runtime_utilities.flags;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;

// This class is just a centralized container for any Flags that will be parsed on behalf of the current program's
// declaration of flags in .claro_module_api files for modules composing the program.
public class $Flags {

  // Flags will be lazily parsed upon first usage.
  private static ImmutableMap<String, Object> $parsedOptions = null;
  private static boolean $isInitialized = false;
  // This will get statically initialized by the program's generated main class. Care will be taken to ensure that this
  // is statically initialized before any static value that attempts to depend on this class.
  public static Class<? extends OptionsBase> $programOptionsClass = null;


  // The same pattern that's used in codegen for `lazy static` values exported by modules. Unfortunately, Claro's static
  // value initialization scheme would need to be able to access these flags as well, but based on the time in Java's
  // startup lifecycle when they're actually initialized, there'd be no reliable mechanism to actually let the main
  // method run any logic to put the flags passed into main anywhere. So the flags themselves will need to be retrieved
  // from a JVM System Property that is not necessarily portable. For now, this actually imposes the first (obvious)
  // portability issue for Claro.
  public static synchronized ImmutableMap<String, Object> lazyStaticInitializer$parsedOptions() {
    if (!$Flags.$isInitialized) {
      // TODO(steving) Determine some better mechanism than the "sun.java.command" System Property to get args before
      //   Claro's static value initializers run. If that's not possible, then Claro codegen will have to implement its own
      //   static value initialization logic in the main method, so that the flags can definitely be accessed and parsed
      //   before their initializers are triggered.
      ImmutableList<String> argsList = ImmutableList.copyOf(System.getProperty("sun.java.command").split(" "));
      // Getting the flags from this system property includes the actual name of the program as the first arg, drop it.
      String[] args = argsList.subList(1, argsList.size()).toArray(new String[]{});
      OptionsParser parser = OptionsParser.newOptionsParser($Flags.$programOptionsClass);
      // Report flag parsing errors and immediately exit because I don't want *all* flags usage in Claro to be forced to
      // model the possibility that they're absent. If a flag is necessary and no default was specified, then its
      // absence should be a terminal error.
      parser.parseAndExitUponError(args);

      $Flags.$parsedOptions = ImmutableMap.copyOf(parser.getOptions($Flags.$programOptionsClass).asMap());
      $Flags.$isInitialized = true;
    }
    return $Flags.$parsedOptions;
  }
}
