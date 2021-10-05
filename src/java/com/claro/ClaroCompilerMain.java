package com.claro;

import com.claro.compiler_backends.interpreted.Interpreter;
import com.claro.compiler_backends.java_source.JavaSourceCompilerBackend;
import com.claro.compiler_backends.repl.Repl;

import java.util.Arrays;

public class ClaroCompilerMain {
  public static void main(String[] args) throws Exception {
    // TODO(steving) Determine which backend to use based on args.
    String[] argsCopy = Arrays.copyOfRange(args, 1, args.length);
    String compilerBackend = args[0].substring(2);
    switch (compilerBackend) {
      case "interpreted":
        new Interpreter(argsCopy).run();
        break;
      case "java_source":
        new JavaSourceCompilerBackend(argsCopy).run();
        break;
      case "repl":
        new Repl().run();
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "Unsupported compiler backend requested (%s). Must be oneof \"interpreted\"/\"java_source\"/\"repl\".",
                compilerBackend
            )
        );
    }
  }
}
