package com.claro.intermediate_representation.expressions.term;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.internal_static_state.InternalStaticStateUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PipeChainBackreferenceTerm extends Term {

  // If the PipeChainStmt determines that the current stage should backreference the previous stage via an intermediate
  // identifier (because this stage has multiple backreference usages) then this will be populated to be just an
  // identifier reference, otherwise, if there's just a single backreference in this stage, then this will be a proper
  // codegen call to the previous expr.
  private AtomicReference<BiFunction<ScopedHeap, Boolean, Object>> prevPipeChainStageBackreferenceCodegenFn;

  public PipeChainBackreferenceTerm(
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!InternalStaticStateUtil.PipeChainStmt_withinPipeChainContext) {
      throw ClaroTypeException.forBackreferenceOutsideOfValidPipeChainContext();
    }

    InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount++;
    prevPipeChainStageBackreferenceCodegenFn =
        InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageCodegenFn;
    try {
      return InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType.get();
    } catch (NullPointerException e) {
      // This is gnarly but oh well... instead of requiring a check every time, we'll just wait until we fail.
      // We're basing this on the invariant, maintained by PipeChainStmt, that the backreferenced expr will always
      // be set before type checking if there is one present.
      throw new IllegalStateException(
          "Invalid Pipe Chain Source: The pipe chain source may not use backreferences (`^`). The source stage has no" +
          " pipe chain stage to refer back to."
      );
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // We lookup the backreferenced expr again since the PipeChainStmt might be swapping out the Expr between
    // type checking and codegen in the case of a stage needing to be backreferenced multiple times.
    return (GeneratedJavaSource) prevPipeChainStageBackreferenceCodegenFn.get()
        .apply(scopedHeap, /*shouldJavaSourceCodegen=*/true);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // We lookup the backreferenced expr again since the PipeChainStmt might be swapping out the Expr between
    // type checking and codegen in the case of a stage needing to be backreferenced multiple times.
    throw new ClaroParserException("PipeChainBackreferenceTerm Not yet implemented!");
  }
}
