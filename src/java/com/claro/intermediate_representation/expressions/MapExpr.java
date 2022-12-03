package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroMap;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

public class MapExpr extends Expr {
  private final ImmutableList<ImmutableList<Expr>> initializerKeyValPairs;
  private Optional<Type> assertedType = Optional.empty();
  private Types.MapType validatedMapType;

  public MapExpr(ImmutableList<ImmutableList<Expr>> initializerKeyValPairs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.initializerKeyValPairs = initializerKeyValPairs;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // Definitely have a map here, the user can't lie and call it something else. Early check here before type inference
    // only in the case of an empty initializer since we'll give a better error message in the non-empty case if waiting
    // until after inference.
    if (this.initializerKeyValPairs.isEmpty() && !expectedExprType.baseType().equals(BaseType.MAP)) {
      logTypeError(new ClaroTypeException(BaseType.MAP, expectedExprType));
      return;
    }
    // Just grabbing the asserted type in case this was defined as an empty map in which case I don't know the type unless it's asserted.
    this.assertedType = Optional.of(expectedExprType);
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) In the future as I'm polishing the stdlib, I need to have static assertions that keys are always
    //  some immutable data type.
    if (this.initializerKeyValPairs.isEmpty()) {
      if (!this.assertedType.isPresent()) {
        // There would be no way to infer the type of this empty map, so this isn't allowed. Type assertion needed.
        throw ClaroTypeException.forUndecidedTypeLeakEmptyMapInitialization();
      }

      this.validatedMapType = (Types.MapType) this.assertedType.get();
      return this.validatedMapType;
    }

    // Time to validate the initializer list.
    Type expectedKeyType;
    Type expectedValueType;
    if (this.assertedType.isPresent()) {
      expectedKeyType = this.assertedType.get().parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS);
      expectedValueType = this.assertedType.get().parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES);
    } else {
      expectedKeyType = this.initializerKeyValPairs.get(0).get(0).getValidatedExprType(scopedHeap);
      expectedValueType = this.initializerKeyValPairs.get(0).get(1).getValidatedExprType(scopedHeap);
    }
    // Now we know which types everything in the initializer list should have, just check everything. Throw specific
    // errors on each key/value where there's a type mismatch.
    for (ImmutableList<Expr> kvPair : this.initializerKeyValPairs) {
      kvPair.get(0).assertExpectedExprType(scopedHeap, expectedKeyType);
      kvPair.get(1).assertExpectedExprType(scopedHeap, expectedValueType);
    }

    this.validatedMapType = Types.MapType.forKeyValueTypes(expectedKeyType, expectedValueType);
    return validatedMapType;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "new ClaroMap<%s, %s>(%s)",
                this.validatedMapType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
                    .getJavaSourceType(),
                this.validatedMapType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
                    .getJavaSourceType(),
                this.validatedMapType.getJavaSourceClaroType()
            )));
    if (!this.initializerKeyValPairs.isEmpty()) {
      for (ImmutableList<Expr> kv : this.initializerKeyValPairs) {
        GeneratedJavaSource keyGenJavaSource = kv.get(0).generateJavaSourceOutput(scopedHeap);
        GeneratedJavaSource valueGenJavaSource = kv.get(1).generateJavaSourceOutput(scopedHeap);
        StringBuilder putKVJavaSourceBody =
            new StringBuilder(
                String.format(
                    ".set(%s, %s)",
                    keyGenJavaSource.javaSourceBody(),
                    valueGenJavaSource.javaSourceBody()
                ));
        // Now that I've consumed java source bodies of both key and value, I can consume them.
        keyGenJavaSource.javaSourceBody().setLength(0);
        valueGenJavaSource.javaSourceBody().setLength(0);
        // Now, make sure that the prefix/static java source stmts will be executed in the order of key first then value.
        res = res
            .createMerged(keyGenJavaSource)
            .createMerged(valueGenJavaSource)
            .createMerged(GeneratedJavaSource.forJavaSourceBody(putKVJavaSourceBody));
      }
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    ClaroMap<Object, Object> res = new ClaroMap<>(this.validatedMapType);
    this.initializerKeyValPairs.forEach(
        kv -> res.put(kv.get(0).generateInterpretedOutput(scopedHeap), kv.get(1)
            .generateInterpretedOutput(scopedHeap)));
    return res;
  }
}
