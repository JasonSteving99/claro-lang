package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.ClaroParserException;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.Stack;

public class StructuralConcreteGenericTypeValidationUtil {
  public static Type validateArgExprsAndExtractConcreteGenericTypeParams(
      HashMap<Type, Type> genericTypeParamTypeHashMap,
      Type functionExpectedArgType,
      Type actualArgExprType) throws ClaroTypeException {
    return validateArgExprsAndExtractConcreteGenericTypeParams(
        genericTypeParamTypeHashMap, functionExpectedArgType, actualArgExprType, false);
  }

  public static Type validateArgExprsAndExtractConcreteGenericTypeParams(
      HashMap<Type, Type> genericTypeParamTypeHashMap,
      Type functionExpectedArgType,
      Type actualArgExprType,
      boolean inferConcreteTypes) throws ClaroTypeException {
    return validateArgExprsAndExtractConcreteGenericTypeParams(
        genericTypeParamTypeHashMap, functionExpectedArgType, actualArgExprType, inferConcreteTypes, Optional.empty(), Optional.empty(), Optional.empty(), false);
  }

  public static Type validateArgExprsAndExtractConcreteGenericTypeParams(
      HashMap<Type, Type> genericTypeParamTypeHashMap,
      Type functionExpectedArgType,
      Type actualArgExprType,
      boolean inferConcreteTypes,
      // The below should only ever be used by dynamic dispatch codegen for the sake of deciding the call path that
      // would produce the types we're looking for (according to what's listed in `genericTypeParamTypeHashMap`).
      Optional<HashMap<Type, ImmutableList<ImmutableList<StringBuilder>>>> optionalTypeCheckingCodegenForDynamicDispatch,
      Optional<Stack<ImmutableList<StringBuilder>>> optionalTypeCheckingCodegenPath,
      Optional<HashMap<Type, Boolean>> optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap,
      boolean withinNestedCollectionTypeNotSupportingDynDispatch
  ) throws ClaroTypeException {
    ClaroTypeException DEFAULT_TYPE_MISMATCH_EXCEPTION =
        new ClaroTypeException("Couldn't construct matching concrete type");
    // These nested types are types that we'll structurally recurse into to search for concrete usages
    // of the generic type params.
    Type validatedReturnType = null;
    final ImmutableSet<BaseType> nestedBaseTypes =
        ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION, BaseType.FUTURE, BaseType.TUPLE, BaseType.LIST, BaseType.MAP, BaseType.SET, BaseType.STRUCT, BaseType.USER_DEFINED_TYPE);
    // In the case that this positional arg is a generic param type, then actually we need to just accept
    // whatever type is in the passed arg expr.
    if (functionExpectedArgType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      // First, let's make note of whether this is a oneof being found within a nested collection type.
      if (withinNestedCollectionTypeNotSupportingDynDispatch
          && optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap.isPresent()
          && actualArgExprType.baseType().equals(BaseType.ONEOF)) {
        optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap.get()
            .put(functionExpectedArgType, true);
      }

      if (genericTypeParamTypeHashMap.containsKey(functionExpectedArgType)) {
        // The type of this particular generic param has already been determined by an earlier arg over the
        // same generic type param, so actually this arg expr MUST have the same type.
        if (inferConcreteTypes) {
          return genericTypeParamTypeHashMap.get(functionExpectedArgType);
        } else if (actualArgExprType.equals(genericTypeParamTypeHashMap.get(functionExpectedArgType))) {
          optionalTypeCheckingCodegenForDynamicDispatch.ifPresent(
              // We only actually want the FIRST path to getting this type.
              // TODO(steving) Future optimization opportunity: don't just take the FIRST path, take the SHORTEST.
              //   Note that the only reason I'm not already doing this now is because the same map is getting passed in
              //   across multiple args now, so if we just took the shortest based on local logic here, codegen wouldn't
              //   know which arg it was supposed to be grabbing the type from.
              m -> m.putIfAbsent(actualArgExprType, ImmutableList.copyOf(optionalTypeCheckingCodegenPath.get())));
          return actualArgExprType;
        } else {
          throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
        }
      } else {
        genericTypeParamTypeHashMap.put((Types.$GenericTypeParam) functionExpectedArgType, actualArgExprType);
        return actualArgExprType;
      }
    } else if (nestedBaseTypes.contains(functionExpectedArgType.baseType())) {
      // We're going to need to do structural type validation to derive the expected generic type.

      // First I just need to validate that the base types are actually even matching.
      if (!actualArgExprType.baseType().equals(functionExpectedArgType.baseType())) {
        // If they're not matching in the sense that the actual arg is a oneof type and the expected arg is not, then
        // try doing structural matching against all of the oneof variant types.
        if (actualArgExprType.baseType().equals(BaseType.ONEOF) &&
            !withinNestedCollectionTypeNotSupportingDynDispatch) {
          // We shouldn't just always allow oneofs instead of concrete types, because not all of Claro has been upgraded
          // to actually acknowledge the fact that oneofs can be passed into generic type params yet.
          HashMultimap<Type, Type> genericTypeParamAcceptedTypes = HashMultimap.create();
          HashMap<Type, Type> tmpVariantCheckingGenTypeParamMap = Maps.newHashMap(genericTypeParamTypeHashMap);

          // First, we need to make sure that we track the codegen path that we are just about to go down.
          if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
            optionalTypeCheckingCodegenPath.get().push(
                ImmutableList.of(
                    new StringBuilder("((Types.OneofType) "),
                    // Leave room for the previous type to be nested here.
                    new StringBuilder(").getVariantTypes()")
                ));
          }
          for (Type actualOneofVariantType : ((Types.OneofType) actualArgExprType).getVariantTypes()) {
            // Collect any generic type params that we manage to collect from this arg in the tmp map.
            validateArgExprsAndExtractConcreteGenericTypeParams(
                tmpVariantCheckingGenTypeParamMap, functionExpectedArgType, actualOneofVariantType, inferConcreteTypes, optionalTypeCheckingCodegenForDynamicDispatch, optionalTypeCheckingCodegenPath, optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap, withinNestedCollectionTypeNotSupportingDynDispatch);
            Sets.difference(tmpVariantCheckingGenTypeParamMap.entrySet(), genericTypeParamTypeHashMap.entrySet())
                .forEach(g -> genericTypeParamAcceptedTypes.put(g.getKey(), g.getValue()));
            tmpVariantCheckingGenTypeParamMap = Maps.newHashMap(genericTypeParamTypeHashMap);
          }
          // Undo the codegen path as we unwind the stack.
          if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
            optionalTypeCheckingCodegenPath.get().pop();
          }

          // If all of the above checks panned out without throwing, then we know that this oneof was actually valid.
          // So we just need to update the inferred generic type params to be oneof all of the types that were accepted.
          Sets.difference(genericTypeParamAcceptedTypes.keySet(), genericTypeParamTypeHashMap.keySet())
              .forEach(
                  g -> genericTypeParamTypeHashMap.put(
                      g, Types.OneofType.forVariantTypes(ImmutableList.copyOf(genericTypeParamAcceptedTypes.get(g)))));
          return actualArgExprType;
        } else {
          // Otherwise, it's a straight-up unrecoverable type mismatch, bail now.
          throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
        }
      } else if (
          functionExpectedArgType.parameterizedTypeArgs() != null
          && !(functionExpectedArgType.baseType().equals(actualArgExprType.baseType())
               && functionExpectedArgType.parameterizedTypeArgs().size()
                  == actualArgExprType.parameterizedTypeArgs().size())) {
        // If base types are same but we definitely have different parameterized type arg sizes, we can't have a match.
        // It's a straight-up unrecoverable type mismatch, bail now.
        throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
      }

      // Now recurse into the structure to check internal types.
      switch (functionExpectedArgType.baseType()) {
        case FUNCTION:
        case PROVIDER_FUNCTION:
          // First, we need to make sure that we track the codegen path that we are just about to go down.
          if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
            optionalTypeCheckingCodegenPath.get().push(
                ImmutableList.of(
                    new StringBuilder("((Types.ProcedureType) "),
                    // Leave room for the previous type to be nested here.
                    new StringBuilder(").getReturnType()")
                ));
          }
          validatedReturnType =
              validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap,
                  ((Types.ProcedureType) functionExpectedArgType).getReturnType(),
                  ((Types.ProcedureType) actualArgExprType).getReturnType(),
                  inferConcreteTypes,
                  optionalTypeCheckingCodegenForDynamicDispatch,
                  optionalTypeCheckingCodegenPath,
                  optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap,
                  /*withinNestedCollectionTypeNotSupportingDynDispatch=*/ true
              );
          // Undo the codegen path as we unwind the stack.
          if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
            optionalTypeCheckingCodegenPath.get().pop();
          }

          if (functionExpectedArgType.baseType().equals(BaseType.PROVIDER_FUNCTION)) {
            // Providers have no args so don't fall into the next checks.
            return Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                validatedReturnType, ((Types.ProcedureType) actualArgExprType).getAnnotatedBlocking());
          }
          // Intentional fallthrough - cuz hey programming should be fun and cute sometimes. Fight me, future Jason.
        case CONSUMER_FUNCTION: // Both FUNCTION and CONSUMER_FUNCTION need to validate args, so do it once here.
          ImmutableList<Type> expectedArgTypes = ((Types.ProcedureType) functionExpectedArgType).getArgTypes();
          ImmutableList<Type> actualArgTypes = ((Types.ProcedureType) actualArgExprType).getArgTypes();
          // First check that we have the matching number of args.
          if (actualArgTypes.size() != expectedArgTypes.size()) {
            throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
          }
          ImmutableList.Builder<Type> validatedArgTypes = ImmutableList.builder();
          for (int i = 0; i < expectedArgTypes.size(); i++) {
            // First, we need to make sure that we track the codegen path that we are just about to go down.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().push(
                  ImmutableList.of(
                      new StringBuilder("((Types.ProcedureType) "),
                      // Leave room for the previous type to be nested here.
                      new StringBuilder(").getArgTypes().get(").append(i).append(')')
                  ));
            }
            validatedArgTypes.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap,
                    expectedArgTypes.get(i),
                    actualArgTypes.get(i),
                    inferConcreteTypes,
                    optionalTypeCheckingCodegenForDynamicDispatch,
                    optionalTypeCheckingCodegenPath,
                    optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap,
                    /*withinNestedCollectionTypeNotSupportingDynDispatch=*/ true
                ));
            // Undo the codegen path as we unwind the stack.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().pop();
            }
          }
          Types.ProcedureType actualArgExprProcedureType = (Types.ProcedureType) actualArgExprType;
          if (functionExpectedArgType.baseType().equals(BaseType.CONSUMER_FUNCTION)) {
            return Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                validatedArgTypes.build(),
                actualArgExprProcedureType.getAnnotatedBlocking(),
                actualArgExprProcedureType.getAnnotatedBlockingGenericOverArgs(),
                actualArgExprProcedureType.getGenericProcedureArgNames()
            );
          }
          return Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
              validatedArgTypes.build(),
              validatedReturnType,
              actualArgExprProcedureType.getAnnotatedBlocking(),
              actualArgExprProcedureType.getAnnotatedBlockingGenericOverArgs(),
              actualArgExprProcedureType.getGenericProcedureArgNames()
          );
        case STRUCT:
          // Unfortunately couldn't model struct via parameterizedTypeArgs() as I needed to respect ordering of fields,
          // so custom handling for STRUCT is needed.
          Types.StructType expectedStructType = (Types.StructType) functionExpectedArgType;
          Types.StructType actualStructType = (Types.StructType) actualArgExprType;
          if (!expectedStructType.getFieldNames().equals(actualStructType.getFieldNames())) {
            throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
          }
          if (((SupportsMutableVariant<?>) expectedStructType).isMutable()
              != ((SupportsMutableVariant<?>) actualStructType).isMutable()) {
            throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
          }
          ImmutableList.Builder<Type> validatedFieldTypesBuilder = ImmutableList.builder();
          for (int i = 0; i < expectedStructType.getFieldTypes().size(); i++) {
            // First, we need to make sure that we track the codegen path that we are just about to go down.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().push(
                  ImmutableList.of(
                      new StringBuilder("((Types.StructType) "),
                      // Leave room for the previous type to be nested here.
                      new StringBuilder(").getFieldTypes().get(").append(i).append(')')
                  ));
            }
            validatedFieldTypesBuilder.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap,
                    expectedStructType.getFieldTypes().get(i),
                    actualStructType.getFieldTypes().get(i),
                    inferConcreteTypes,
                    optionalTypeCheckingCodegenForDynamicDispatch,
                    optionalTypeCheckingCodegenPath,
                    optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap,
                    /*withinNestedCollectionTypeNotSupportingDynDispatch=*/ true
                ));
            // Undo the codegen path as we unwind the stack.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().pop();
            }
          }

          return Types.StructType.forFieldTypes(actualStructType.getFieldNames(), validatedFieldTypesBuilder.build(), actualStructType.isMutable());
        case FUTURE: // TODO(steving) Actually, all types should be able to be validated in this way... THIS is how I had originally set out to implement Types
        case LIST:   //  as nested structures that self-describe. If they all did this, there could be a single case instead of a switch.
        case MAP:
        case SET:
        case TUPLE:
        case USER_DEFINED_TYPE:
          ImmutableList<Type> expectedParameterizedArgTypes =
              functionExpectedArgType.parameterizedTypeArgs().values().asList();
          ImmutableList<Type> actualParameterizedArgTypes = actualArgExprType.parameterizedTypeArgs().values().asList();

          ImmutableList.Builder<Type> validatedParameterizedArgTypesBuilder = ImmutableList.builder();
          for (int i = 0; i < functionExpectedArgType.parameterizedTypeArgs().size(); i++) {
            // First, we need to make sure that we track the codegen path that we are just about to go down.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().push(
                  ImmutableList.of(
                      new StringBuilder(),
                      // Leave room for the previous type to be nested here.
                      new StringBuilder(".parameterizedTypeArgs().values().asList().get(").append(i).append(')')
                  ));
            }
            validatedParameterizedArgTypesBuilder.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap,
                    expectedParameterizedArgTypes.get(i),
                    actualParameterizedArgTypes.get(i),
                    inferConcreteTypes,
                    optionalTypeCheckingCodegenForDynamicDispatch,
                    optionalTypeCheckingCodegenPath,
                    optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap,
                    /*withinNestedCollectionTypeNotSupportingDynDispatch=*/ true
                ));
            // Undo the codegen path as we unwind the stack.
            if (optionalTypeCheckingCodegenForDynamicDispatch.isPresent()) {
              optionalTypeCheckingCodegenPath.get().pop();
            }
          }
          // Need to ensure that if we're matching against a collection type that has both mutable and immutable
          // variants, that the actual arg type matches the expected arg type's mutable modifier.
          if (functionExpectedArgType instanceof SupportsMutableVariant &&
              ((SupportsMutableVariant<?>) functionExpectedArgType).isMutable()
              != ((SupportsMutableVariant<?>) actualArgExprType).isMutable()) {
            throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
          }
          ImmutableList<Type> validatedParameterizedArgTypes = validatedParameterizedArgTypesBuilder.build();
          switch (functionExpectedArgType.baseType()) {
            case FUTURE:
              return Types.FutureType.wrapping(validatedParameterizedArgTypes.get(0));
            case LIST:
              return Types.ListType.forValueType(validatedParameterizedArgTypes.get(0), ((SupportsMutableVariant<?>) functionExpectedArgType).isMutable());
            case MAP:
              return Types.MapType.forKeyValueTypes(
                  validatedParameterizedArgTypes.get(0), validatedParameterizedArgTypes.get(1), ((SupportsMutableVariant<?>) functionExpectedArgType).isMutable());
            case SET:
              return Types.SetType.forValueType(validatedParameterizedArgTypes.get(0), ((SupportsMutableVariant<?>) functionExpectedArgType).isMutable());
            case TUPLE:
              return Types.TupleType.forValueTypes(validatedParameterizedArgTypes, ((SupportsMutableVariant<?>) functionExpectedArgType).isMutable());
            case USER_DEFINED_TYPE:
              return Types.UserDefinedType.forTypeNameAndParameterizedTypes(((Types.UserDefinedType) functionExpectedArgType).getTypeName(), validatedParameterizedArgTypes);
          }
        default:
          throw new ClaroParserException("Internal Compiler Error: I'm missing handling a case that requires structural type validation when validating a call to a generic function and inferring the concrete type params.");
      }
    } else {
      // Otherwise, this is not a generic type param position, and we need to validate this arg against the
      // actual concrete type in the function signature.
      if (actualArgExprType.equals(functionExpectedArgType)) {
        // Before returning the validated type, I still need to actually update the `genericTypeParamTypeHashMap` just
        // to maintain this function's contract on all paths. Since I know the two are equal, I can just add any generic
        // types straight into the map as itself.
        if (actualArgExprType.baseType().equals(BaseType.ONEOF)) {
          ImmutableList.Builder<Type> variantTypesBuilder = ImmutableList.builder();
          // I need to ensure that any Generic Type Params get converted to their concrete types if possible.
          for (Type actualOneofTypeVariant : ((Types.OneofType) actualArgExprType).getVariantTypes()) {
            variantTypesBuilder.add(validateArgExprsAndExtractConcreteGenericTypeParams(
                genericTypeParamTypeHashMap, actualOneofTypeVariant, actualOneofTypeVariant, inferConcreteTypes, optionalTypeCheckingCodegenForDynamicDispatch, optionalTypeCheckingCodegenPath, optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap, withinNestedCollectionTypeNotSupportingDynDispatch));
          }
          return Types.OneofType.forVariantTypes(variantTypesBuilder.build());
        }
        return actualArgExprType;
      } else if (
          functionExpectedArgType.baseType().equals(BaseType.ONEOF)
          && (((Types.OneofType) functionExpectedArgType).getVariantTypes().contains(actualArgExprType)
              || (actualArgExprType.baseType().equals(BaseType.ONEOF)
                  && ((Types.OneofType) functionExpectedArgType).getVariantTypes()
                      .containsAll(((Types.OneofType) actualArgExprType).getVariantTypes())))) {
        return actualArgExprType;
      } else if (
          functionExpectedArgType.baseType().equals(BaseType.ONEOF)
          && !actualArgExprType.baseType().equals(BaseType.ONEOF)
          && ((Types.OneofType) functionExpectedArgType).getVariantTypes().stream()
                 .filter(t -> t.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))
                 .collect(ImmutableList.toImmutableList())
                 .size() == 1) {
        // Here, we need to try structurally accepting that an expected oneof might match a concrete type as one of the
        // accepted variants. However, it WOULD HAVE ALREADY MATCHED IN THE PREVIOUS CONDITION if there was a concrete
        // variant accepting this type, so clearly it could only work if the expected oneof itself accepts a generic
        // type. So just iterate structural matching over any generic type params. Note that this wouldn't work if there
        // was more than one generic type in the oneof (because we wouldn't know which generic type param that it
        // matched) so we'll need to fail this conservatively in that case.
        Type expectedOneofGenericTypeVariant =
            ((Types.OneofType) functionExpectedArgType).getVariantTypes().stream()
                .filter(t -> t.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))
                .findFirst().get();
        Type res = validateArgExprsAndExtractConcreteGenericTypeParams(
            genericTypeParamTypeHashMap, expectedOneofGenericTypeVariant, actualArgExprType, inferConcreteTypes, optionalTypeCheckingCodegenForDynamicDispatch, optionalTypeCheckingCodegenPath, optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap, withinNestedCollectionTypeNotSupportingDynDispatch);
        return res;
      } else if (
          functionExpectedArgType.baseType().equals(BaseType.ONEOF)
          && actualArgExprType.baseType().equals(BaseType.ONEOF)
          && ((Types.OneofType) functionExpectedArgType).getVariantTypes().size()
             == ((Types.OneofType) actualArgExprType).getVariantTypes().size()) {
        // The last thing I'll try is supporting inference between oneofs with multiple generic type params in the
        // *LIMITED CASE* that all of the type variants are in the same order.
        // TODO(steving) Likely, in the future I may want to extend this to somehow try all ordering combinations of
        //   variants to see if I can get to a full match, but for now that's not supported.
        ImmutableList.Builder<Type> variantTypesBuilder = ImmutableList.builder();
        for (int i = 0; i < ((Types.OneofType) functionExpectedArgType).getVariantTypes().size(); i++) {
          variantTypesBuilder.add(validateArgExprsAndExtractConcreteGenericTypeParams(
              genericTypeParamTypeHashMap, ((Types.OneofType) functionExpectedArgType).getVariantTypes()
                  .asList()
                  .get(i), ((Types.OneofType) actualArgExprType).getVariantTypes()
                  .asList()
                  .get(i), inferConcreteTypes, optionalTypeCheckingCodegenForDynamicDispatch, optionalTypeCheckingCodegenPath, optionalIsTypeParamEverUsedWithinNestedCollectionTypeMap, withinNestedCollectionTypeNotSupportingDynDispatch));
        }
        return Types.OneofType.forVariantTypes(variantTypesBuilder.build());
      }
      throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
    }
  }
}