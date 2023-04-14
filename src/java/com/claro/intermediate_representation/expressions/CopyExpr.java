package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CopyExpr extends Expr {
  private final Expr copiedExpr;
  private Type validatedCopiedExprType;

  public CopyExpr(Expr copiedExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.copiedExpr = copiedExpr;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) Allow type coercion of mutability annotation by type assertion.
    return this.validatedCopiedExprType = this.copiedExpr.getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource copiedExprJavaSource = this.copiedExpr.generateJavaSourceOutput(scopedHeap);
    return getCopyJavaSource(copiedExprJavaSource, validatedCopiedExprType, /*nestingLevel=*/0).orElse(copiedExprJavaSource);
  }

  private static Optional<GeneratedJavaSource> getCopyJavaSource(GeneratedJavaSource copiedExprJavaSource, Type copiedExprType, long nestingLevel) {
    if (copiedExprType instanceof SupportsMutableVariant ||
        copiedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      // Here I have some structured type that actually has internal values to be copied. First let's find out if those
      // values are deeply-immutable (returns Optional.empty()) or if we get back some codegen that'll copy the nested
      // elements inside this collection.
      switch (copiedExprType.baseType()) {
        case LIST:
          Optional<GeneratedJavaSource> optionalElementCopyCodegen =
              getCopyJavaSource(
                  GeneratedJavaSource.forJavaSourceBody(
                      new StringBuilder("$elem")
                          .append(nestingLevel + 1)),
                  ((Types.ListType) copiedExprType).getElementType(),
                  nestingLevel + 1
              );
          GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroList(")
                  .append(copiedExprType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegen.isPresent()) {
            // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
            copiedExprJavaSource.javaSourceBody()
                .append(".stream().map($elem").append(nestingLevel + 1).append(" -> ")
                .append(optionalElementCopyCodegen.get().javaSourceBody().toString())
                .append(").collect(Collectors.toList()))");
          } else {
            if (!((Types.ListType) copiedExprType).isMutable()) {
              return Optional.empty();
            }
            copiedExprJavaSource.javaSourceBody()
                .append(")");
          }
          res = res.createMerged(copiedExprJavaSource);
          return Optional.of(res);
        case SET:
          optionalElementCopyCodegen =
              getCopyJavaSource(
                  GeneratedJavaSource.forJavaSourceBody(
                      new StringBuilder("$elem").append(nestingLevel + 1)),
                  copiedExprType.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE),
                  nestingLevel + 1
              );
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroSet(")
                  .append(copiedExprType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegen.isPresent()) {
            // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
            copiedExprJavaSource.javaSourceBody()
                .append(".stream().map($elem").append(nestingLevel + 1).append(" -> ")
                .append(optionalElementCopyCodegen.get().javaSourceBody().toString())
                .append(").collect(Collectors.toList()))");
          } else {
            if (!((Types.SetType) copiedExprType).isMutable()) {
              return Optional.empty();
            }
            copiedExprJavaSource.javaSourceBody()
                .append(")");
          }
          res = res.createMerged(copiedExprJavaSource);
          return Optional.of(res);
        case MAP:
          StringBuilder tupToKey =
              new StringBuilder("((")
                  .append(
                      copiedExprType.parameterizedTypeArgs()
                          .get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
                          .getJavaSourceType())
                  .append(") $t").append(nestingLevel + 1).append(".getElement(0))");
          Optional<GeneratedJavaSource> optionalKeyCopyCodegen =
              getCopyJavaSource(
                  GeneratedJavaSource.forJavaSourceBody(tupToKey),
                  copiedExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS),
                  nestingLevel + 1
              );
          StringBuilder tupToVal =
              new StringBuilder("((")
                  .append(
                      copiedExprType.parameterizedTypeArgs()
                          .get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
                          .getJavaSourceType())
                  .append(") $t").append(nestingLevel + 1).append(".getElement(1))");
          Optional<GeneratedJavaSource> optionalValCopyCodegen =
              getCopyJavaSource(
                  GeneratedJavaSource.forJavaSourceBody(tupToVal),
                  copiedExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES),
                  nestingLevel + 1
              );
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroMap(")
                  .append(copiedExprType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (!(optionalKeyCopyCodegen.isPresent() || optionalValCopyCodegen.isPresent())) {
            if (!((Types.MapType) copiedExprType).isMutable()) {
              return Optional.empty();
            }
            copiedExprJavaSource.javaSourceBody()
                .append(")");
          } else {
            // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
            copiedExprJavaSource.javaSourceBody()
                .append(".stream().collect(ImmutableMap.toImmutableMap($t").append(nestingLevel + 1).append(" -> ")
                .append(optionalKeyCopyCodegen
                            .orElse(GeneratedJavaSource.forJavaSourceBody(tupToKey))
                            .javaSourceBody()
                            .toString())
                .append(", $t").append(nestingLevel + 1).append(" -> ")
                .append(optionalValCopyCodegen
                            .orElse(GeneratedJavaSource.forJavaSourceBody(tupToVal))
                            .javaSourceBody()
                            .toString())
                .append(")))");
          }
          res = res.createMerged(copiedExprJavaSource);
          return Optional.of(res);
        case TUPLE:
        case STRUCT:
          String copiedTupleValSyntheticVar = "$t_" + (nestingLevel + 1);
          ImmutableList<Optional<GeneratedJavaSource>> optionalElementCopyCodegens =
              IntStream.range(
                      0,
                      copiedExprType.baseType().equals(BaseType.TUPLE)
                      ? copiedExprType.parameterizedTypeArgs().size()
                      : ((Types.StructType) copiedExprType).getFieldTypes().size()
                  )
                  .boxed()
                  .map(
                      i -> {
                        Type currElementType =
                            copiedExprType.baseType().equals(BaseType.TUPLE)
                            ? copiedExprType.parameterizedTypeArgs().get(String.format("$%s", i))
                            : ((Types.StructType) copiedExprType).getFieldTypes().get(i);
                        return getCopyJavaSource(
                            GeneratedJavaSource.forJavaSourceBody(
                                new StringBuilder("((")
                                    .append(currElementType.getJavaSourceType())
                                    .append(") ")
                                    .append(copiedTupleValSyntheticVar)
                                    .append(copiedExprType.baseType().equals(BaseType.TUPLE)
                                            ? ".getElement("
                                            : ".values[")
                                    .append(i)
                                    .append(copiedExprType.baseType().equals(BaseType.TUPLE) ? ")" : "]")
                                    .append(")")),
                            currElementType,
                            nestingLevel + 1
                        );
                      }).collect(ImmutableList.toImmutableList());
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new Claro")
                  .append(copiedExprType.baseType().equals(BaseType.TUPLE) ? "Tuple" : "Struct")
                  .append("(")
                  .append(copiedExprType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegens.stream().noneMatch(Optional::isPresent)
              && !((SupportsMutableVariant<?>) copiedExprType).isMutable()) {
            return Optional.empty();
          }
          // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
          res.javaSourceBody()
              .append("((Function<")
              .append(copiedExprType.getJavaSourceType())
              .append(", Object[]>) ")
              .append(copiedTupleValSyntheticVar)
              .append(" -> new Object[] {")
              .append(
                  IntStream.range(
                          0,
                          copiedExprType.baseType().equals(BaseType.TUPLE)
                          ? copiedExprType.parameterizedTypeArgs().size()
                          : ((Types.StructType) copiedExprType).getFieldTypes().size()
                      )
                      .boxed()
                      .map(
                          i -> optionalElementCopyCodegens.get(i)
                              .map(GeneratedJavaSource::javaSourceBody)
                              .orElseGet(() ->
                                             new StringBuilder(
                                                 String.format(
                                                     copiedExprType.baseType().equals(BaseType.TUPLE)
                                                     ? "%s.getElement(%s)"
                                                     : "%s.values[%s]",
                                                     copiedTupleValSyntheticVar,
                                                     i
                                                 )))
                      )
                      .collect(Collectors.joining(", "))
              )
              .append("}).apply(");
          copiedExprJavaSource.javaSourceBody()
              .append("))");
          res = res.createMerged(copiedExprJavaSource);
          return Optional.of(res);
        case USER_DEFINED_TYPE:
          Types.UserDefinedType copiedExprUserDefinedType = (Types.UserDefinedType) copiedExprType;
          // User defined types are represented in a somewhat complex way when parameterized. So in that case, set the
          // GenericTypeParam's type mapping before codegen here to refer to this UserDefinedType's concrete types.
          Optional<Map<Type, Type>> originalGenTypeCodegenMappings
              = Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen;
          if (!copiedExprUserDefinedType.parameterizedTypeArgs().isEmpty()) {
            ImmutableList<String> typeParamNames =
                Types.UserDefinedType.$typeParamNames.get(copiedExprUserDefinedType.getTypeName());
            Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen =
                Optional.of(
                    IntStream.range(0, copiedExprUserDefinedType.parameterizedTypeArgs().size()).boxed()
                        .collect(ImmutableMap.toImmutableMap(
                            i -> Types.$GenericTypeParam.forTypeParamName(typeParamNames.get(i)),
                            i -> copiedExprUserDefinedType.parameterizedTypeArgs().get(i.toString())
                        )));
          }

          try {
            Type wrappedType =
                copiedExprUserDefinedType.parameterizedTypeArgs().isEmpty()
                ? Types.UserDefinedType.$resolvedWrappedTypes.get(copiedExprUserDefinedType.getTypeName())
                : StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                    Maps.newHashMap(Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen.get()),
                    Types.UserDefinedType.$resolvedWrappedTypes.get(copiedExprUserDefinedType.getTypeName()),
                    Types.UserDefinedType.$resolvedWrappedTypes.get(copiedExprUserDefinedType.getTypeName()),
                    true
                );
            Optional<GeneratedJavaSource> optionalWrappedValCopyCodegen =
                getCopyJavaSource(
                    GeneratedJavaSource.forJavaSourceBody(
                        new StringBuilder(copiedExprJavaSource.javaSourceBody().toString())
                            .append(".wrappedValue")),
                    wrappedType,
                    nestingLevel + 1
                );

            // It seems a bit odd, but in some way all User Defined Types are inherently immutable. The only way to
            // "change" one is to unwrap it and then wrap it again, but that rewrap creates a new $UserDefinedType
            // instance.
            Optional<GeneratedJavaSource> copyUserDefinedTypeCodegenRes =
                optionalWrappedValCopyCodegen
                    .map(
                        generatedJavaSource -> GeneratedJavaSource.forJavaSourceBody(
                            new StringBuilder("new $UserDefinedType(\"")
                                .append(copiedExprUserDefinedType.getTypeName())
                                .append("\", ")
                                .append(copiedExprUserDefinedType.parameterizedTypeArgs().values().stream()
                                            .map(Type::getJavaSourceClaroType)
                                            .collect(Collectors.joining(", ", "ImmutableList.of(", "), ")))
                                .append(Types.UserDefinedType.$resolvedWrappedTypes.get(copiedExprUserDefinedType.getTypeName())
                                            .getJavaSourceClaroType())
                                .append(", ")
                                .append(generatedJavaSource.javaSourceBody())
                                .append(")")
                        ));

            // Reset GenericTypeParam state.
            if (!copiedExprUserDefinedType.parameterizedTypeArgs().isEmpty()) {
              Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen = originalGenTypeCodegenMappings;
            }
            return copyUserDefinedTypeCodegenRes;
          } catch (ClaroTypeException e) {
            throw new RuntimeException(e);
          }
        default:
          throw new RuntimeException("Internal Compiler Error: Unsupported structured type found in CopyExpr!");
      }
    } else {
      // This isn't a structured type that actually supports copying.
      return Optional.empty();
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl copy when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support copy() in the interpreted backend just yet!");
  }
}
