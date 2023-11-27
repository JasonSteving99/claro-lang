package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CopyExpr extends Expr {
  private final Expr copiedExpr;
  private Type validatedCopiedExprType;
  private Optional<Type> assertedCopyResultType = Optional.empty();

  public CopyExpr(Expr copiedExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.copiedExpr = copiedExpr;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // Support type coercion of mutability annotation.
    this.assertedCopyResultType = Optional.of(expectedExprType);
    this.getValidatedExprType(scopedHeap);

    if (this.validatedCopiedExprType.baseType().equals(BaseType.FUTURE)) {
      // We actually don't support any mutability coercion for a future as we don't even really support copying futures.
      // Futures are "special" values which I need to develop a better vocabulary around to explain why they can't be
      // copied like other values....the main reason is that they're not values....they're the representation of a value
      // you'll have in the future...
      super.assertExpectedExprType(scopedHeap, expectedExprType);
    }

    // If the validated type is structurally equivalent to the asserted type MODULO THE MUTABILITY ANNOTATIONS, then
    // we'll say that this copy is valid (possibly under some mutability coercion).
    if (this.validatedCopiedExprType instanceof SupportsMutableVariant<?>) {
      if (this.assertedCopyResultType.get() instanceof SupportsMutableVariant<?>
          && ((SupportsMutableVariant<?>) this.validatedCopiedExprType).toDeeplyImmutableVariant()
              .equals(((SupportsMutableVariant<?>) this.assertedCopyResultType.get()).toDeeplyImmutableVariant())) {
        // Found a match (perhaps under mutability coercion).
        return;
      }
      this.logTypeError(new ClaroTypeException(this.validatedCopiedExprType, this.assertedCopyResultType.get()));
    }
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return this.validatedCopiedExprType = this.copiedExpr.getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource copiedExprJavaSource = this.copiedExpr.generateJavaSourceOutput(scopedHeap);
    return getCopyJavaSource(copiedExprJavaSource, this.validatedCopiedExprType, this.assertedCopyResultType.orElse(this.validatedCopiedExprType), /*nestingLevel=*/0).orElse(copiedExprJavaSource);
  }

  public static Optional<GeneratedJavaSource> getCopyJavaSource(GeneratedJavaSource copiedExprJavaSource, Type copiedExprType, Type coercedType, long nestingLevel) {
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // !!!WARNING!!!!
    // IF YOU UPDATE THIS CONDITION, YOU MUST ALSO UPDATE THE CONDITION CODEGEN'D FOR ONEOF HANDLING BELOW!
    // !!!WARNING!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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
                  ((Types.ListType) coercedType).getElementType(),
                  nestingLevel + 1
              );
          GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroList(")
                  .append(coercedType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegen.isPresent()) {
            // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
            copiedExprJavaSource.javaSourceBody()
                .append(".stream().map($elem").append(nestingLevel + 1).append(" -> ")
                .append(optionalElementCopyCodegen.get().javaSourceBody().toString())
                .append(").collect(Collectors.toList()))");
          } else {
            if (!((Types.ListType) copiedExprType).isMutable() && !((Types.ListType) coercedType).isMutable()) {
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
                  coercedType.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE),
                  nestingLevel + 1
              );
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroSet(")
                  .append(coercedType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegen.isPresent()) {
            // Here we've found some elements that aren't deeply-immutable, so we need to copy them.
            copiedExprJavaSource.javaSourceBody()
                .append(".stream().map($elem").append(nestingLevel + 1).append(" -> ")
                .append(optionalElementCopyCodegen.get().javaSourceBody().toString())
                .append(").collect(Collectors.toList()))");
          } else {
            if (!((Types.SetType) copiedExprType).isMutable() && !((Types.SetType) coercedType).isMutable()) {
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
                  coercedType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS),
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
                  coercedType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES),
                  nestingLevel + 1
              );
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new ClaroMap(")
                  .append(coercedType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (!(optionalKeyCopyCodegen.isPresent() || optionalValCopyCodegen.isPresent())) {
            if (!((Types.MapType) copiedExprType).isMutable() && !((Types.MapType) coercedType).isMutable()) {
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
                        Type currCoercedElementType =
                            coercedType.baseType().equals(BaseType.TUPLE)
                            ? coercedType.parameterizedTypeArgs().get(String.format("$%s", i))
                            : ((Types.StructType) coercedType).getFieldTypes().get(i);
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
                            currCoercedElementType,
                            nestingLevel + 1
                        );
                      }).collect(ImmutableList.toImmutableList());
          res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("new Claro")
                  .append(coercedType.baseType().equals(BaseType.TUPLE) ? "Tuple" : "Struct")
                  .append("(")
                  .append(coercedType.getJavaSourceClaroType())
                  .append(", ")
          );
          if (optionalElementCopyCodegens.stream().noneMatch(Optional::isPresent)
              && !((SupportsMutableVariant<?>) copiedExprType).isMutable() &&
              !((SupportsMutableVariant<?>) coercedType).isMutable()) {
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
          Type wrappedType;
          Type wrappedCoercedType;
          try {
            if (!copiedExprUserDefinedType.parameterizedTypeArgs().isEmpty()) {
              ImmutableList<String> typeParamNames =
                  Types.UserDefinedType.$typeParamNames.get(
                      String.format(
                          "%s$%s",
                          copiedExprUserDefinedType.getTypeName(),
                          copiedExprUserDefinedType.getDefiningModuleDisambiguator()
                      ));
              Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen =
                  Optional.of(
                      IntStream.range(0, copiedExprUserDefinedType.parameterizedTypeArgs().size()).boxed()
                          .collect(ImmutableMap.toImmutableMap(
                              i -> Types.$GenericTypeParam.forTypeParamName(typeParamNames.get(i)),
                              i -> copiedExprUserDefinedType.parameterizedTypeArgs().get(i.toString())
                          )));
              wrappedType =
                  StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                      Maps.newHashMap(Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen.get()),
                      Types.UserDefinedType.$resolvedWrappedTypes.get(
                          String.format("%s$%s", copiedExprUserDefinedType.getTypeName(), copiedExprUserDefinedType.getDefiningModuleDisambiguator())),
                      Types.UserDefinedType.$resolvedWrappedTypes.get(
                          String.format("%s$%s", copiedExprUserDefinedType.getTypeName(), copiedExprUserDefinedType.getDefiningModuleDisambiguator())),
                      true
                  );
              Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen =
                  Optional.of(
                      IntStream.range(0, coercedType.parameterizedTypeArgs().size()).boxed()
                          .collect(ImmutableMap.toImmutableMap(
                              i -> Types.$GenericTypeParam.forTypeParamName(typeParamNames.get(i)),
                              i -> coercedType.parameterizedTypeArgs().get(i.toString())
                          )));
              wrappedCoercedType =
                  StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                      Maps.newHashMap(Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen.get()),
                      Types.UserDefinedType.$resolvedWrappedTypes.get(
                          String.format(
                              "%s$%s",
                              ((Types.UserDefinedType) coercedType).getTypeName(),
                              ((Types.UserDefinedType) coercedType).getDefiningModuleDisambiguator()
                          )),
                      Types.UserDefinedType.$resolvedWrappedTypes.get(
                          String.format(
                              "%s$%s",
                              ((Types.UserDefinedType) coercedType).getTypeName(),
                              ((Types.UserDefinedType) coercedType).getDefiningModuleDisambiguator()
                          )),
                      true
                  );
            } else {
              wrappedType = Types.UserDefinedType.$resolvedWrappedTypes.get(
                  String.format("%s$%s", copiedExprUserDefinedType.getTypeName(), copiedExprUserDefinedType.getDefiningModuleDisambiguator()));
              wrappedCoercedType =
                  Types.UserDefinedType.$resolvedWrappedTypes.get(
                      String.format(
                          "%s$%s",
                          ((Types.UserDefinedType) coercedType).getTypeName(),
                          ((Types.UserDefinedType) coercedType).getDefiningModuleDisambiguator()
                      ));
            }
          } catch (ClaroTypeException e) {
            throw new RuntimeException("Internal Compiler Error! This should be unreachable. Type validation should've already caught this mismatch.", e);
          }
          Optional<GeneratedJavaSource> optionalWrappedValCopyCodegen =
              getCopyJavaSource(
                  GeneratedJavaSource.forJavaSourceBody(
                      new StringBuilder(copiedExprJavaSource.javaSourceBody().toString())
                          .append(".wrappedValue")),
                  wrappedType,
                  wrappedCoercedType,
                  nestingLevel + 1
              );

          // It seems a bit odd, but in some way all User Defined Types are inherently immutable. The only way to
          // "change" one is to unwrap it and then wrap it again, but that rewrap creates a new $UserDefinedType
          // instance.
          Optional<GeneratedJavaSource> copyUserDefinedTypeCodegenRes =
              optionalWrappedValCopyCodegen
                  .map(
                      generatedJavaSource -> {
                        // Depending on whether this type def was parsed from a dep module, this may have been named with a
                        // disambiguating prefix like "$DEP_MODULE$module$". For the sake of codegen'd values containing consistent
                        // names everywhere, I need to strip any prefixing here so that instances of this type created ANYWHERE will
                        // definitely evaluate as having the same type.
                        String canonicalizedTypeName = copiedExprUserDefinedType.getTypeName()
                            .substring(copiedExprUserDefinedType.getTypeName().lastIndexOf("$") + 1);
                        return GeneratedJavaSource.forJavaSourceBody(
                            new StringBuilder("new $UserDefinedType(\"")
                                .append(canonicalizedTypeName)
                                .append("\", \"")
                                .append(copiedExprUserDefinedType.getDefiningModuleDisambiguator())
                                .append("\", ")
                                .append(copiedExprUserDefinedType.parameterizedTypeArgs().values().stream()
                                            .map(Type::getJavaSourceClaroType)
                                            .collect(Collectors.joining(", ", "ImmutableList.of(", "), ")))
                                .append(
                                    Types.UserDefinedType.$resolvedWrappedTypes.get(
                                            String.format(
                                                "%s$%s",
                                                copiedExprUserDefinedType.getTypeName(),
                                                copiedExprUserDefinedType.getDefiningModuleDisambiguator()
                                            ))
                                        .getJavaSourceClaroType())
                                .append(", ")
                                .append(generatedJavaSource.javaSourceBody())
                                .append(")")
                        );
                      });

          // Reset GenericTypeParam state.
          if (!copiedExprUserDefinedType.parameterizedTypeArgs().isEmpty()) {
            Types.$GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen = originalGenTypeCodegenMappings;
          }
          return copyUserDefinedTypeCodegenRes;
        default:
          throw new RuntimeException("Internal Compiler Error: Unsupported structured type found in CopyExpr!");
      }
    } else {
      if (copiedExprType.baseType().equals(BaseType.ONEOF)) {
        // Oneofs aren't "structured" per se, but the variants within may be, so gen logic to detect which variant type
        // you actually have so that you can handle it properly.

        String syntheticOneofVar = "$oneofVal_" + (nestingLevel + 1);
        ImmutableMap<Type, GeneratedJavaSource> variantCopyCodegens =
            IntStream.range(0, ((Types.OneofType) copiedExprType).getVariantTypes().size()).boxed()
                .collect(
                    ImmutableMap.toImmutableMap(
                        i -> ((Types.OneofType) copiedExprType).getVariantTypes().asList().get(i),
                        i -> {
                          Type variant = ((Types.OneofType) copiedExprType).getVariantTypes().asList().get(i);
                          Type coercedVariant = ((Types.OneofType) coercedType).getVariantTypes().asList().get(i);
                          return getCopyJavaSource(
                              GeneratedJavaSource.forJavaSourceBody(
                                  new StringBuilder("((")
                                      .append(variant.getJavaSourceType())
                                      .append(") ")
                                      .append(syntheticOneofVar)
                                      .append(")")),
                              variant,
                              coercedVariant,
                              nestingLevel + 1
                          );
                        }
                    )).entrySet().stream()
                // Just make sure that this map *only* contains entries that *actually* require deep copying.
                .filter(entry -> entry.getValue().isPresent())
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> entry.getValue().get()));

        // A few different things might happen to try to slightly optimize gen'd code handling oneofs (not trying to be
        // perfect here, just trying to avoid obviously dumb code). Try not to generate unnecessary checks.

        if (variantCopyCodegens.isEmpty()) {
          // Here, this means that none of the variants *actually* require deep copying, and we can just signal that the
          // original value should be re-referenced.
          return Optional.empty();
        }
        String syntheticOneofTypeVar = "$oneofType_" + (nestingLevel + 1);
        GeneratedJavaSource res =
            GeneratedJavaSource.forJavaSourceBody(
                new StringBuilder()
                    .append("((Function<")
                    .append(copiedExprType.getJavaSourceType())
                    .append(", ")
                    .append(copiedExprType.getJavaSourceType())
                    .append(">) (")
                    .append(syntheticOneofVar)
                    .append(") -> { Type ")
                    .append(syntheticOneofTypeVar)
                    .append(" = ClaroRuntimeUtilities.getClaroType(")
                    .append(syntheticOneofVar)
                    .append("); "));

        // If all variants required deep-copying, then we wouldn't need to check for the case where we just return the
        // original reference, so only codegen that check if it's possible to go either way.
        if (variantCopyCodegens.size() < ((Types.OneofType) copiedExprType).getVariantTypes().size()) {
          res.javaSourceBody()
              .append("if (")
              .append(syntheticOneofTypeVar)
              .append(" instanceof SupportsMutableVariant || ")
              .append(syntheticOneofTypeVar)
              .append(".baseType().equals(BaseType.USER_DEFINED_TYPE)) { ");
        }

        // Now, to handle the case where deep-copying is necessary, again do some minor optimization to at least drop
        // a conditional if there's only a single variant requiring deep-copying.
        if (variantCopyCodegens.size() == 1) {
          res.javaSourceBody()
              .append("return ")
              .append(variantCopyCodegens.values().stream().findFirst().get().javaSourceBody())
              .append(";");
        } else {
          // Unfortunately there are multiple potential variants that would require deep-copying, so we'll need to
          // codegen some conditionals.
          // TODO(steving) In the future, it may be possible to optimize this further to avoid all of these if-stmts.
          //   For example, it might be possible to do a switch over the hashcodes of the variant types...the issue w/
          //   that specific idea is that the hashcode impl for Claro's Types would have to be extremely stable in order
          //   to avoid invalidating compatibility btwn code generated by Claro versions before/after hashcode impl
          //   change. I'll admit I genuinely don't understand the implications of that yet, so I'll avoid the mess as
          //   I'm not ready to claim I am prepared for the consequences or that the hashcode impls are already perfect.
          List<Map.Entry<Type, GeneratedJavaSource>> variantCopyCodegensList = variantCopyCodegens.entrySet().asList();
          res.javaSourceBody()
              .append(
                  IntStream.range(0, variantCopyCodegens.size() - 1)
                      .boxed()
                      .map(
                          i ->
                              new StringBuilder()
                                  .append("if (")
                                  .append(syntheticOneofTypeVar)
                                  .append(".equals(")
                                  .append(variantCopyCodegensList.get(i).getKey().getJavaSourceClaroType())
                                  .append(")) { return ")
                                  .append(variantCopyCodegensList.get(i).getValue().javaSourceBody())
                                  .append("; } ")
                                  .toString()
                      )
                      .collect(Collectors.joining(" else ")))
              .append(" else { return ")
              .append(variantCopyCodegensList.get(variantCopyCodegensList.size() - 1).getValue().javaSourceBody())
              .append("; }");
        }

        // If all variants required deep-copying, then we wouldn't need to check for the case where we just return the
        // original reference, so only codegen that check if it's possible to go either way.
        if (variantCopyCodegens.size() < ((Types.OneofType) copiedExprType).getVariantTypes().size()) {
          res.javaSourceBody()
              .append(" } else { return ")
              .append(syntheticOneofVar)
              .append("; }");
        }

        res.javaSourceBody()
            .append("}).apply(");
        res = res.createMerged(copiedExprJavaSource);
        res.javaSourceBody()
            .append(")");

        return Optional.of(res);
      }

      // This isn't a structured type that actually "requires" copying, signal to just reference the original value.
      return Optional.empty();
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl copy when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support copy() in the interpreted backend just yet!");
  }
}
