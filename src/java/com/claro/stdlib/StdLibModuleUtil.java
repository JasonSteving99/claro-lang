package com.claro.stdlib;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;

import java.util.Optional;

public class StdLibModuleUtil {
  // Don't you just love a good long ass name?
  public static void validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo(
      String optionalStdlibModuleName, Optional<Expr> optionalExprForLogging) throws ClaroTypeException {
    if (ScopedHeap.currProgramDepModules.rowKeySet().contains(optionalStdlibModuleName)) {
      // Type validation worked out, signal that the optional stdlib dep module `http` is being used so that the user
      // doesn't get any errors indicating that they're not using a module they've placed a dep on.
      ScopedHeap.markDepModuleUsed(optionalStdlibModuleName);
    } else {
      // The user actually hasn't placed an explicit dep `optional_stdlib_deps = ["http"]` on their claro_*() target so
      // even though types check out, the build wouldn't have the appropriate Java runtime deps. Signal an error that
      // the user is required to explicitly place a dep for this optional stdlib module so that the Java deps can be
      // added to their build.
      ClaroTypeException err =
          ClaroTypeException.forUsingOptionalStdlibDepModuleWithoutExplicitBuildDep(optionalStdlibModuleName);
      if (optionalExprForLogging.isPresent()) {
        optionalExprForLogging.get().logTypeError(err);
      } else {
        throw err;
      }
    }
  }

  // TODO(steving) Drop this.
  // This literally just exists to make it more convenient for Lexers to call into this while we're hacking the optional stdlib modules.
  public static void validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo(String optionalStdlibModuleName) {
    try {
      validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo(optionalStdlibModuleName, Optional.empty());
    } catch (ClaroTypeException e) {
      throw new RuntimeException(e);
    }
  }
}
