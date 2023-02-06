package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.ClaroParserException;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;

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
    ClaroTypeException DEFAULT_TYPE_MISMATCH_EXCEPTION =
        new ClaroTypeException("Couldn't construct matching concrete type");
    // These nested types are types that we'll structurally recurse into to search for concrete usages
    // of the generic type params.
    Type validatedReturnType = null;
    final ImmutableSet<BaseType> nestedBaseTypes =
        ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION, BaseType.FUTURE, BaseType.TUPLE, BaseType.LIST, BaseType.MAP);
    if (functionExpectedArgType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      // In the case that this positional arg is a generic param type, then actually we need to just accept
      // whatever type is in the passed arg expr.
      if (genericTypeParamTypeHashMap.containsKey(functionExpectedArgType)) {
        // The type of this particular generic param has already been determined by an earlier arg over the
        // same generic type param, so actually this arg expr MUST have the same type.
        if (inferConcreteTypes) {
          return genericTypeParamTypeHashMap.get(functionExpectedArgType);
        } else if (actualArgExprType.equals(genericTypeParamTypeHashMap.get(functionExpectedArgType))) {
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
        throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
      }

      // Now recurse into the structure to check internal types.
      switch (functionExpectedArgType.baseType()) {
        case FUNCTION:
        case PROVIDER_FUNCTION:
          validatedReturnType =
              validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap,
                  ((Types.ProcedureType) functionExpectedArgType).getReturnType(),
                  ((Types.ProcedureType) actualArgExprType).getReturnType(),
                  inferConcreteTypes
              );
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
            validatedArgTypes.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap, expectedArgTypes.get(i), actualArgTypes.get(i), inferConcreteTypes));
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
        case FUTURE: // TODO(steving) Actually, all types should be able to be validated in this way... THIS is how I had originally set out to implement Types
        case LIST:   //  as nested structures that self-describe. If they all did this, there could be a single case instead of a switch.
        case TUPLE:
        case MAP:
          ImmutableList<Type> expectedParameterizedArgTypes =
              functionExpectedArgType.parameterizedTypeArgs().values().asList();
          ImmutableList<Type> actualParameterizedArgTypes = actualArgExprType.parameterizedTypeArgs().values().asList();

          ImmutableList.Builder<Type> validatedParameterizedArgTypesBuilder = ImmutableList.builder();
          for (int i = 0; i < functionExpectedArgType.parameterizedTypeArgs().size(); i++) {
            validatedParameterizedArgTypesBuilder.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap,
                    expectedParameterizedArgTypes.get(i),
                    actualParameterizedArgTypes.get(i),
                    inferConcreteTypes
                ));
          }
          ImmutableList<Type> validatedParameterizedArgTypes = validatedParameterizedArgTypesBuilder.build();
          switch (functionExpectedArgType.baseType()) {
            case FUTURE:
              return Types.FutureType.wrapping(validatedParameterizedArgTypes.get(0));
            case LIST:
              return Types.ListType.forValueType(validatedParameterizedArgTypes.get(0));
            case TUPLE:
              return Types.TupleType.forValueTypes(validatedParameterizedArgTypes);
            case MAP:
              return Types.MapType.forKeyValueTypes(
                  validatedParameterizedArgTypes.get(0), validatedParameterizedArgTypes.get(1));
          }
        default:
          throw new ClaroParserException("Internal Compiler Error: I'm missing handling a case that requires structural type validation when validating a call to a generic function and inferring the concrete type params.");
      }
    } else {
      // Otherwise, this is not a generic type param position, and we need to validate this arg against the
      // actual concrete type in the function signature.
      if (!actualArgExprType.equals(functionExpectedArgType)) {
        throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
      }
      return actualArgExprType;
    }
  }
}