package com.claro.stdlib;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.NewTypeDefStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import java.util.Map;
import java.util.Optional;

public class StdLibUtil {

  public static boolean setupBuiltinTypes = false;

  public static ImmutableList<Stmt> registerIdentifiers(ScopedHeap scopedHeap) {
    for (Table.Cell<String, Type, Object> stdLibProcedure
        : StdLibRegistry.stdLibProcedureTypes.cellSet()) {
      registerStdLibSymbolTableEntry(
          scopedHeap,
          stdLibProcedure.getRowKey(),
          stdLibProcedure.getColumnKey(),
          stdLibProcedure.getValue(),
          /*isTypeDef=*/false
      );
    }
    for (Map.Entry<String, Type> stdLibTypeDef : StdLibRegistry.stdLibTypeDefs.entrySet()) {
      registerStdLibSymbolTableEntry(
          scopedHeap, stdLibTypeDef.getKey(), stdLibTypeDef.getValue(), null, /*isTypeDef=*/true);
    }

    // TODO(steving) This is getting overly complicated just b/c I don't want to fix Riju's config. Go update
    //   Riju so that this can all be simplified.
    if (StdLibUtil.setupBuiltinTypes) {
      // These Stmts will get automatically prefixed to the beginning of the program to setup the "stdlib".
      return ImmutableList.of(
          new NewTypeDefStmt(
              "Error",
              /*optionalOriginatingModuleDisambiguator=*/Optional.of(""),
              TypeProvider.Util.getTypeByName("T", /*isTypeDefinition=*/true), ImmutableList.of("T")
          ),
          new NewTypeDefStmt(
              "ParsedJson",
              /*optionalOriginatingModuleDisambiguator=*/Optional.of(""),
              (TypeProvider) (scopedHeap1) ->
                  Types.StructType.forFieldTypes(
                      ImmutableList.of("result", "rawJson"),
                      ImmutableList.of(
                          Types.OneofType.forVariantTypes(
                              ImmutableList.of(
                                  TypeProvider.Util.getTypeByName("T", /*isTypeDefinition=*/true)
                                      .resolveType(scopedHeap1),
                                  Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                                      "Error",
                                      // TODO(steving) This is going to be problematic once I begin building out the stdlib modules.
                                      /*definingModuleDisambiguator=*/"", // No module for stdlib types that weren't moved into Modules yet.
                                      ImmutableList.of(Types.STRING)
                                  )
                              )
                          ),
                          Types.STRING
                      ),
                      /*isMutable=*/false
                  ),
              ImmutableList.of("T")
          )
      );
    } else {
      // This is much better handled by using Claro source files instead. These builtin types are actually found at
      // com/claro/stdlib/builtin_types.claro_internal.
      return ImmutableList.of();
    }
  }

  private static void registerStdLibSymbolTableEntry(
      ScopedHeap scopedHeap, String symbolName, Type type, Object value, boolean isTypeDef) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(symbolName),
        String.format(
            "Internal Compiler Error: Unexpected redeclaration of std-lib entry %s %s.",
            symbolName,
            type
        )
    );

    // Finally mark the function declared and initialized within the original calling scope.
    scopedHeap.putIdentifierValue(symbolName, type, value);
    if (isTypeDef) {
      scopedHeap.markIdentifierAsTypeDefinition(symbolName);
    }
  }
}
