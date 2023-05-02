package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FromJsonExpr extends Expr {
  private final Expr parsedExpr;
  private Type assertedParsedResultType;
  private Type assertedTargetType;

  public FromJsonExpr(Expr parsedExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.parsedExpr = parsedExpr;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    this.assertedParsedResultType = expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (this.assertedParsedResultType == null) {
      this.logTypeError(ClaroTypeException.forIllegalParseFromJSONWithNoTargetTypeAssertion());
    }

    // Obviously I can only parse from JSON strings.
    this.parsedExpr.assertExpectedExprType(scopedHeap, Types.STRING);

    Type expectedResultType = Types.UserDefinedType.forTypeNameAndParameterizedTypes(
        "ParsedJson",
        ImmutableList.of(Types.$GenericTypeParam.forTypeParamName("T"))
    );
    HashMap<Type, Type> targetConcreteTypeMap = Maps.newHashMap();
    Type resultType;
    try {
      resultType = StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
          targetConcreteTypeMap,
          expectedResultType,
          this.assertedParsedResultType
      );
      this.assertedTargetType = targetConcreteTypeMap.get(Types.$GenericTypeParam.forTypeParamName("T"));
    } catch (ClaroTypeException e) {
      return expectedResultType;
    }

    // The only thing I need to do is walk the structure as ensure that it'd even be possible to parse some JSON to this
    // requested type automatically.
    try {
      validateJSONParsingIsPossible(this.assertedTargetType);
    } catch (ClaroTypeException e) {
      this.logTypeError(e);
      return Types.UNKNOWABLE;
    }

    // This will always either return the parsed type if parsing is successful, else it'll fallback to returning an
    // Error wrapping the original JSON string so the user can handle it manually if they want.
    return resultType;
  }

  private void validateJSONParsingIsPossible(Type type) throws ClaroTypeException {
    switch (type.baseType()) {
      case BOOLEAN:
      case INTEGER:
      case FLOAT:
      case STRING:
      case NOTHING:
        return;
      case LIST:
        validateJSONParsingIsPossible(((Types.ListType) type).getElementType());
        return;
      case STRUCT:
        for (Type fieldType : ((Types.StructType) type).getFieldTypes()) {
          validateJSONParsingIsPossible(fieldType);
        }
        return;
      case ONEOF:
        // TODO(steving) Consider revisiting this decision and implementing a more robust parser that will handle arbitrary oneofs.
        // Claro can support a very limited lookahead for parsing oneofs. I'm not going to implement a complex recursive
        // backtracking parser, so instead, if it wouldn't be trivial to distinguish all variants from one another using
        // a single JsonToken that Gson provides via JsonReader::peek(), then I'll reject the format. This essentially
        // can be boiled down to the simple restriction that your oneof may only have up to a single list type and up to
        // a single struct type - and additionally it cannot have both an int and float variant as the single
        // JsonToken::NUMBER is used to represent both.
        for (Type variantType : ((Types.OneofType) type).getVariantTypes()) {
          validateJSONParsingIsPossible(variantType);
        }
        if (((Types.OneofType) type).getVariantTypes()
                .stream()
                .filter(t -> t.baseType().equals(BaseType.LIST))
                .count() > 1
            || ((Types.OneofType) type).getVariantTypes()
                   .stream()
                   .filter(t -> t.baseType().equals(BaseType.STRUCT))
                   .count() > 1) {
          throw ClaroTypeException.forIllegalParseFromJSONForUnsupportedOneofType(type, this.assertedTargetType);
        }
        if (((Types.OneofType) type).getVariantTypes().containsAll(ImmutableList.of(Types.INTEGER, Types.FLOAT))) {
          throw ClaroTypeException.forIllegalParseFromJSONForUnsupportedOneofType(type, this.assertedTargetType);
        }
        return;
      case MAP:
        if (type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS).equals(Types.STRING)) {
          validateJSONParsingIsPossible(type.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES));
          return;
        }
      default:
        throw ClaroTypeException.forIllegalParseFromJSONForUnsupportedType(this.assertedTargetType);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder("((Function<String, ")
            .append(this.assertedParsedResultType.getJavaSourceType()) // ParsedJson<TargetType>
            .append(">) $jsonString -> {\n")
            .append("\tcom.google.gson.stream.JsonReader $jsonReader = new com.google.gson.stream.JsonReader(new StringReader($jsonString));\n")
            .append(getParseJSONJavaSource(this.assertedTargetType, 0, /*alreadyPeekedType=*/ false))
            .append("\n}).apply("));
    // TODO(steving) Consider some way to handle the "non-execute Prefix"
    //  https://www.javadoc.io/doc/com.google.code.gson/gson/2.8.0/com/google/gson/stream/JsonReader.html#nonexecuteprefix
    //  Gson's builtin setLenient(true) is too permissive in that it'll allow malformed JSON.
//            .append("\tjsonReader.setLenient(true);\n"));
    res = res.createMerged(this.parsedExpr.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(")");
    return res;
  }

  private static StringBuilder getParseJSONJavaSource(Type type, int nestingLevel, boolean alreadyPeekedType) {
    final String GSON_TOKEN = "com.google.gson.stream.JsonToken";
    StringBuilder res = new StringBuilder()
        .append("try {\n");
    if (!alreadyPeekedType) {
      res.append(GSON_TOKEN).append(" $peeked").append(nestingLevel).append(" = $jsonReader.peek();\n");
    }
    BiFunction<Type, Boolean, StringBuilder> getFieldParserForType = (fieldType, _alreadyPeeked) ->
        new StringBuilder()
            .append("((Supplier<$UserDefinedType<ClaroStruct>>) () -> {\n")
            .append("\t\t")
            .append(getParseJSONJavaSource(fieldType, nestingLevel + 1, /*alreadyPeekedType=*/ _alreadyPeeked))
            .append("\t}).get();");
    switch (type.baseType()) {
      case BOOLEAN:
        if (!alreadyPeekedType) {
          res.append("if (").append(GSON_TOKEN).append(".BOOLEAN.equals($peeked").append(nestingLevel).append(")) {\n");
        }
        res.append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.nextBoolean(), $jsonString);\n");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case INTEGER:
        if (!alreadyPeekedType) {
          res.append("if (").append(GSON_TOKEN).append(".NUMBER.equals($peeked").append(nestingLevel).append(")) {\n");
        }
        res.append("\ttry {\n")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.nextInt(), $jsonString);\n")
            .append("\t} catch (java.lang.NumberFormatException e) {\n")
            .append("\t\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.getPath(), $jsonString);\n")
            .append("\t}\n");
        if (!alreadyPeekedType) {
          res.append("}");
        }
        break;
      case FLOAT:
        if (!alreadyPeekedType) {
          res.append("if (").append(GSON_TOKEN).append(".NUMBER.equals($peeked").append(nestingLevel).append(")) {\n");
        }
        res.append("\ttry {\n")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.nextDouble(), $jsonString);\n")
            .append("\t} catch (java.lang.NumberFormatException e) {\n")
            .append("\t\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(" $jsonReader.getPath(), $jsonString);\n")
            .append("\t}\n");
        if (!alreadyPeekedType) {
          res.append("}");
        }
        break;
      case STRING:
        if (!alreadyPeekedType) {
          res.append("if (").append(GSON_TOKEN).append(".STRING.equals($peeked").append(nestingLevel).append(")) {\n");
        }
        res.append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.nextString(), $jsonString);\n");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case NOTHING:
        if (!alreadyPeekedType) {
          res.append("if (").append(GSON_TOKEN).append(".NULL.equals($peeked").append(nestingLevel).append(")) {\n");
        }
        res.append("\t$jsonReader.nextNull();\n")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $ClaroNothing.SINGLETON_NOTHING, $jsonString);");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case LIST:
        Type elemType = ((Types.ListType) type).getElementType();
        if (!alreadyPeekedType) {
          res.append("if (")
              .append(GSON_TOKEN)
              .append(".BEGIN_ARRAY.equals($peeked")
              .append(nestingLevel)
              .append(")) {\n");
        }
        res.append("\t$jsonReader.beginArray();\n")
            // This is a fascinating example of a compiler superpower that the users don't have access to. Here,
            // regardless of whether the list is being parsed to a mutable/immutable list, I'm going to append to the
            // ClaroList because I know that I'm the sole owner of this list as I, the compiler, just created it.
            .append("\tClaroList<")
            .append(elemType.getJavaSourceType())
            .append("> $listBuilder")
            .append(nestingLevel)
            .append(" = new ClaroList(")
            .append(type.getJavaSourceClaroType())
            .append(");\n")
            .append("\tfinal Supplier<$UserDefinedType<ClaroStruct>> $parseElement")
            .append(nestingLevel)
            .append(" = () -> {\n")
            .append("\t\t")
            .append(getParseJSONJavaSource(elemType, nestingLevel + 1, /*alreadyPeekedType=*/ false))
            .append("\t};\n")
            .append("\twhile ($jsonReader.hasNext()) {\n")
            .append("\t\t$UserDefinedType<ClaroStruct> $parsedElem")
            .append(nestingLevel)
            .append(" = $parseElement")
            .append(nestingLevel)
            .append(".get();\n")
            .append("\t\tif ($parsedElem")
            .append(nestingLevel)
            .append(".wrappedValue.values[0] instanceof $UserDefinedType) { // It's necessarily an Error<string> since Claro can't parse user-defined types from JSON automatically.\n")
            .append("\t\t\treturn $parsedElem")
            .append(nestingLevel)
            .append(";\n")
            .append("\t\t}\n")
            .append("\t\t$listBuilder")
            .append(nestingLevel)
            .append(".add((")
            .append(elemType.getJavaSourceType())
            .append(") $parsedElem")
            .append(nestingLevel)
            .append(".wrappedValue.values[0]);\n")
            .append("\t}\n")
            .append("\t$jsonReader.endArray();\n")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $listBuilder")
            .append(nestingLevel)
            .append(", $jsonString);\n");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case STRUCT:
        Types.StructType structType = (Types.StructType) type;
        if (!alreadyPeekedType) {
          res.append("if (")
              .append(GSON_TOKEN)
              .append(".BEGIN_OBJECT.equals($peeked")
              .append(nestingLevel)
              .append(")) {\n");
        }
        res.append("\t$jsonReader.beginObject();\n")
            // This is a fascinating example of a compiler superpower that the users don't have access to. Here,
            // regardless of whether the struct is being parsed to mutable/immutable, I'm going to modify the
            // array because I know that I'm the sole owner of this struct as I, the compiler, just created it.
            .append("\tClaroStruct $structBuilder")
            .append(nestingLevel)
            .append(" = new ClaroStruct(")
            .append(type.getJavaSourceClaroType())
            .append(", ")
            .append(IntStream.range(0, structType.getFieldTypes().size())
                        .boxed()
                        .map(i -> "(Object) null")
                        .collect(Collectors.joining(", ")))
            .append(");\n")
            .append("\twhile ($jsonReader.hasNext()) {\n")
            .append("\t\t$UserDefinedType<ClaroStruct> $parsedField")
            .append(nestingLevel)
            .append(";\n")
            .append("\t\tswitch($jsonReader.nextName()) {\n");
        IntStream.range(0, structType.getFieldTypes().size()).boxed().forEach(
            i -> res
                .append("\t\t\tcase \"")
                .append(structType.getFieldNames().get(i))
                .append("\":\n")
                .append("\t\t\t\t$parsedField")
                .append(nestingLevel)
                .append(" = ")
                .append(getFieldParserForType.apply(structType.getFieldTypes().get(i), /*_alreadyPeaked=*/ false))
                // This is just to save some lines of code, but here I'm going to optimistically put whatever we parsed
                // into the struct builder, though we may not end up using it.
                .append("\n\t\t\t\t$structBuilder")
                .append(nestingLevel)
                .append(".values[")
                .append(i)
                .append("] = $parsedField")
                .append(nestingLevel)
                .append(".wrappedValue.values[0];\n")
                .append("\t\t\t\tbreak;\n")
        );
        res.append("\t\t\tdefault: // This is some unexpected field.\n")
            .append("\t\t\t\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.getPath(), $jsonString);\n")
            .append("\t\t}\n")
            .append("\t\tif ($parsedField")
            .append(nestingLevel)
            .append(".wrappedValue.values[0] instanceof $UserDefinedType) { // It's necessarily an Error<string> since Claro can't parse user-defined types from JSON automatically.\n")
            .append("\t\t\treturn $parsedField")
            .append(nestingLevel)
            .append(";\n")
            .append("\t\t}\n")
            .append("\t}\n")
            .append("\t$jsonReader.endObject();\n")
            // Make sure that we validate that *all* required fields were actually set, otherwise the json parsing is
            // considered a failure. Even if the missing field types were `oneof<..., NothingType>`, NothingType only
            // maps to `null` in the JSON representation, a missing field is an error, not auto-coerced to null.
            .append("\tfor (int $i = 0; $i < ")
            .append(structType.getFieldNames().size())
            .append("; ++$i) {\n")
            .append("\t\tif ($structBuilder")
            .append(nestingLevel)
            .append(".values[$i] == null) {\n")
            .append("\t\t\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.getPath(), $jsonString);\n")
            .append("\t\t}")
            .append("\t}")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $structBuilder")
            .append(nestingLevel)
            .append(", $jsonString);\n");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case MAP:
        Types.MapType mapType = (Types.MapType) type;
        if (!alreadyPeekedType) {
          res.append("if (")
              .append(GSON_TOKEN)
              .append(".BEGIN_OBJECT.equals($peeked")
              .append(nestingLevel)
              .append(")) {\n");
        }
        res.append("\t$jsonReader.beginObject();\n")
            // This is a fascinating example of a compiler superpower that the users don't have access to. Here,
            // regardless of whether the map is being parsed to mutable/immutable, I'm going to modify the
            // map because I know that I'm the sole owner of this map as I, the compiler, just created it.
            .append("\tClaroMap $mapBuilder")
            .append(nestingLevel)
            .append(" = new ClaroMap(")
            .append(type.getJavaSourceClaroType())
            .append(");\n")
            .append("\tfinal Supplier<$UserDefinedType<ClaroStruct>> $parseElement")
            .append(nestingLevel)
            .append(" = () -> {\n")
            .append("\t\t")
            .append(getParseJSONJavaSource(
                mapType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES),
                nestingLevel + 1, /*alreadyPeekedType=*/ false
            ))
            .append("\t};\n")
            .append("\twhile ($jsonReader.hasNext()) {\n")
            .append("\t\tString $key")
            .append(nestingLevel)
            .append(" = $jsonReader.nextName();\n")
            .append("\t\t$UserDefinedType<ClaroStruct> $parsedElem")
            .append(nestingLevel)
            .append(" = $parseElement")
            .append(nestingLevel)
            .append(".get();\n")
            .append("\t\tif ($parsedElem")
            .append(nestingLevel)
            .append(".wrappedValue.values[0] instanceof $UserDefinedType) { // It's necessarily an Error<string> since Claro can't parse user-defined types from JSON automatically.\n")
            .append("\t\t\treturn $parsedElem")
            .append(nestingLevel)
            .append(";\n")
            .append("\t\t}\n")
            .append("\t\t$mapBuilder")
            .append(nestingLevel)
            .append(".set($key")
            .append(nestingLevel)
            .append(", (")
            .append(mapType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES).getJavaSourceType())
            .append(") $parsedElem")
            .append(nestingLevel)
            .append(".wrappedValue.values[0]);\n")
            .append("\t}\n")
            .append("\t$jsonReader.endObject();\n")
            .append("\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $mapBuilder")
            .append(nestingLevel)
            .append(", $jsonString);\n");
        if (!alreadyPeekedType) {
          res.append("} ");
        }
        break;
      case ONEOF:
        // Claro can support a very limited lookahead for parsing oneofs. Here we'll assume that validation has already
        // completed so we know that this oneof's variants can be disambiguated with a single peek().
        Types.OneofType oneofType = (Types.OneofType) type;
        res.append("\t$UserDefinedType<ClaroStruct> $parsedOneof").append(nestingLevel).append(";\n")
            .append("\tswitch($peeked").append(nestingLevel).append(") {\n");
        IntStream.range(0, oneofType.getVariantTypes().size()).boxed().forEach(
            i -> {
              res.append("\t\tcase ");
              switch (oneofType.getVariantTypes().asList().get(i).baseType()) {
                case BOOLEAN:
                  res.append("BOOLEAN:\n");
                  break;
                case INTEGER:
                case FLOAT:
                  res.append("NUMBER:\n");
                  break;
                case STRING:
                  res.append("STRING:\n");
                  break;
                case NOTHING:
                  res.append("NULL:\n");
                  break;
                case LIST:
                  res.append("BEGIN_ARRAY:\n");
                  break;
                case STRUCT:
                  res.append("BEGIN_OBJECT:\n");
                  break;
              }
              res.append("\t\t\t$parsedOneof").append(nestingLevel).append(" = ")
                  .append(getFieldParserForType.apply(oneofType.getVariantTypes()
                                                          .asList()
                                                          .get(i), /*_alreadyPeeked=*/ true))
                  .append("\n\t\t\tbreak;\n");
            }
        );
        res.append("\t\tdefault: // This is some unexpected field.\n")
            .append("\t\t\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $jsonReader.getPath(), $jsonString);\n")
            .append("\t}\n")
            .append("\tif ($parsedOneof")
            .append(nestingLevel)
            .append(".wrappedValue.values[0] instanceof $UserDefinedType) { // It's necessarily an Error<string> since Claro can't parse user-defined types from JSON automatically.\n")
            .append("\t\treturn $parsedOneof")
            .append(nestingLevel)
            .append(";\n")
            .append("\t} else {\n")
            .append("\t\treturn ClaroRuntimeUtilities.$getSuccessParsedJson(")
            .append(type.getJavaSourceClaroType())
            .append(", $parsedOneof")
            .append(nestingLevel)
            .append(".wrappedValue.values[0], $jsonString);\n")
            .append("\t} ");
        break;
      default:
        throw new RuntimeException("Internal Compiler Error: Should be unreachable! " + type);
    }
    if (!alreadyPeekedType && !type.baseType().equals(BaseType.ONEOF)) {
      res.append("else { $jsonReader.skipValue(); return ClaroRuntimeUtilities.$getErrorParsedJson(")
          .append(type.getJavaSourceClaroType())
          .append(", $jsonReader.getPath(), $jsonString); }\n");
    }
    return res.append("} catch (java.io.IOException e) {\n\treturn ClaroRuntimeUtilities.$getErrorParsedJson(")
        .append(type.getJavaSourceClaroType())
        .append(", $jsonReader.getPath(), $jsonString); }\n");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl copy when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support copy() in the interpreted backend just yet!");
  }
}
