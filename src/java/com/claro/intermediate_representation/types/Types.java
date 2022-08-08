package com.claro.intermediate_representation.types;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.runtime_utilities.injector.Key;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// TODO(steving) This class needs refactoring into a standalone package.
public final class Types {
  public static final Type INTEGER = ConcreteType.create(BaseType.INTEGER);
  public static final Type FLOAT = ConcreteType.create(BaseType.FLOAT);
  public static final Type STRING = ConcreteType.create(BaseType.STRING);
  public static final Type BOOLEAN = ConcreteType.create(BaseType.BOOLEAN);
  public static final Type MODULE = ConcreteType.create(BaseType.MODULE);

  // Special type that indicates that the compiler won't be able to determine this type answer until runtime at which
  // point it will potentially fail other runtime type checking. Anywhere where an "UNDECIDED" type is emitted by the
  // compiler we'll require a cast on the expr causing the indecision for the programmer to assert they know what's up.
  public static final Type UNDECIDED = ConcreteType.create(BaseType.UNDECIDED);

  public interface Collection {
    Type getElementType();
  }

  @AutoValue
  public abstract static class ListType extends Type implements Collection {
    private static final String PARAMETERIZED_TYPE_KEY = "$values";

    public static ListType forValueType(Type valueType) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of(PARAMETERIZED_TYPE_KEY, valueType));
    }

    @Override
    public Type getElementType() {
      return this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY);
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.ListType.forValueType(%s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY).getJavaSourceClaroType()
      );
    }
  }

  @AutoValue
  public abstract static class TupleType extends Type implements Collection {

    public abstract ImmutableList<Type> getValueTypes();

    public static TupleType forValueTypes(ImmutableList<Type> valueTypes) {
      return new AutoValue_Types_TupleType(BaseType.TUPLE, ImmutableMap.of(), valueTypes);
    }

    @Override
    public Type getElementType() {
      // We literally have no way of determining this type at compile time without knowing which index is being
      // referenced so instead we'll mark this as UNDECIDED.
      return UNDECIDED;
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          getValueTypes().stream().map(Type::toString).collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.TupleType.forValueTypes(ImmutableList.of(%s))",
          Joiner.on(", ")
              .join(this.getValueTypes()
                        .stream()
                        .map(Type::getJavaSourceClaroType)
                        .collect(ImmutableList.toImmutableList()))
      );
    }
  }

  public abstract static class StructType extends Type {

    public abstract String getName();

    public abstract ImmutableMap<String, Type> getFieldTypes();

    private static TypeProvider forFieldTypeProvidersMap(
        ImmutableMap<String, TypeProvider> fieldTypeProvidersMap, String structName, boolean immutable) {
      return (scopedHeap) -> {
        ImmutableMap<String, Type> fieldTypesMap =
            TypeProvider.Util.resolveTypeProviderMap(scopedHeap, fieldTypeProvidersMap);
        Types.StructType resultStructType =
            immutable ?
            Types.StructType.ImmutableStructType.forFieldTypes(structName, fieldTypesMap) :
            Types.StructType.MutableStructType.forFieldTypes(structName, fieldTypesMap);
        scopedHeap.putIdentifierValue(structName, resultStructType, null);
        return resultStructType;
      };
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          getName(),
          getFieldTypes().entrySet().stream()
              .map(stringTypeEntry -> String.format("%s: %s", stringTypeEntry.getKey(), stringTypeEntry.getValue()))
              .collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceType() {
      return String.format(this.baseType().getJavaSourceFmtStr(), this.getName());
    }

    @AutoValue
    public abstract static class ImmutableStructType extends StructType {
      public static ImmutableStructType forFieldTypes(String name, ImmutableMap<String, Type> fieldTypes) {
        return new AutoValue_Types_StructType_ImmutableStructType(
            BaseType.IMMUTABLE_STRUCT, ImmutableMap.of(), name, fieldTypes);
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.StructType.ImmutableStructType.forFieldTypes(\"%s\", ImmutableMap.<String, Type>builder()%s.build())",
            this.getName(),
            this.getFieldTypes()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(\"%s\", %s)", entry.getKey(), entry.getValue()
                    .getJavaSourceClaroType()))
                .collect(Collectors.joining())
        );
      }

      public static TypeProvider forFieldTypeProvidersMap(String structName, ImmutableMap<String, TypeProvider> fieldTypeProvidersMap) {
        return StructType.forFieldTypeProvidersMap(fieldTypeProvidersMap, structName, /*immutable=*/true);
      }
      // TODO(steving) Put some manner of constructor code directly inside this type definition.
    }

    @AutoValue
    public abstract static class MutableStructType extends StructType {
      public static StructType forFieldTypes(String name, ImmutableMap<String, Type> fieldTypes) {
        return new AutoValue_Types_StructType_MutableStructType(BaseType.STRUCT, ImmutableMap.of(), name, fieldTypes);
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.StructType.MutableStructType.forFieldTypes(\"%s\", ImmutableMap.<String, Type>builder()%s.build())",
            this.getName(),
            this.getFieldTypes()
                .entrySet()
                .stream()
                .map(entry -> String.format(".put(\"%s\", %s)", entry.getKey(), entry.getValue()
                    .getJavaSourceClaroType()))
                .collect(Collectors.joining())
        );
      }

      public static TypeProvider forFieldTypeProvidersMap(String structName, ImmutableMap<String, TypeProvider> fieldTypeProvidersMap) {
        return StructType.forFieldTypeProvidersMap(fieldTypeProvidersMap, structName, /*immutable=*/false);
      }
      // TODO(steving) Put some manner of constructor code directly inside this type definition.
    }

  }

  @AutoValue
  public abstract static class BuilderType extends Type {
    private static final ImmutableSet<BaseType> SUPPORTED_BUILT_TYPES =
        ImmutableSet.of(BaseType.STRUCT, BaseType.IMMUTABLE_STRUCT);

    public abstract StructType getBuiltType();

    public static BuilderType forStructType(StructType structType) {
      return new AutoValue_Types_BuilderType(BaseType.BUILDER, ImmutableMap.of(), structType);
    }

    /**
     * This function exists for late binding of user-defined types in the symbol table. This is necessary since we don't
     * have all type information until we parse the entire file and create symbol table entries for all
     * user-defined types.
     *
     * @param structTypeName The name of the (potentially user-defined) type to look for in the symbol table.
     * @return a function that provides the actual resolved BuilderType once the symbol table has all types.
     */
    public static TypeProvider forStructTypeName(String structTypeName) {
      return (scopedHeap) -> {
        Type resolvedTypeFromName = TypeProvider.Util.getTypeByName(
            structTypeName, /*isTypeDefinition=*/true).resolveType(scopedHeap);
        if (!SUPPORTED_BUILT_TYPES.contains(resolvedTypeFromName.baseType())) {
          throw new RuntimeException(new ClaroTypeException(resolvedTypeFromName, SUPPORTED_BUILT_TYPES));
        }
        return BuilderType.forStructType((StructType) resolvedTypeFromName);
      };
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          this.getBuiltType().getName()
      );
    }

    @Override
    public String getJavaSourceType() {
      return String.format(this.baseType().getJavaSourceFmtStr(), this.getBuiltType().getJavaSourceType());
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.BuilderType.forStructType(%s)",
          this.getBuiltType().getJavaSourceClaroType()
      );
    }
  }

  public abstract static class ProcedureType extends Type {

    @Types.Nullable
    public abstract ImmutableList<Type> getArgTypes();

    @Types.Nullable
    public abstract Type getReturnType();

    // This field indicates whether this procedure is *annotated* blocking by the programmer (they may be wrong in which
    // case Claro will fail compilation to force this to be set correctly since Claro takes the philosophical stance
    // that truth on this important feature of a procedure needs to be communicated clearly).
    public abstract Boolean getAnnotatedBlocking();

    // When comparing Types we don't ever want to care about *names* (or other metadata), these are meaningless to the
    // compiler and should be treated equivalently to a user comment in terms of the program's semantic execution. So
    // Make these fields *ignored* by AutoValue so that we can compare function type equality.
    // https://github.com/google/auto/blob/master/value/userguide/howto.md#ignore
    final AtomicReference<Boolean> autoValueIgnoredHasArgs = new AtomicReference<>();

    public boolean hasArgs() {
      return autoValueIgnoredHasArgs.get();
    }

    final AtomicReference<Boolean> autoValueIgnoredHasReturnValue = new AtomicReference<>();

    public boolean hasReturnValue() {
      return autoValueIgnoredHasReturnValue.get();
    }

    final AtomicReference<Optional<BaseType>> autoValueIgnoredOptionalOverrideBaseType =
        new AtomicReference<>(Optional.empty());

    public BaseType getPossiblyOverridenBaseType() {
      return this.autoValueIgnoredOptionalOverrideBaseType.get().orElse(this.baseType());
    }

    // ---------------------- BEGIN PROCEDURE ATTRIBUTES! --------------------- //
    // This field is mutable specifically because we need to be able to update this set first with the set
    // of keys that are directly depended on by this procedure, and then by the set of all of its transitive
    // deps. This is done in multiple phases, because we're parsing a DAG in linear order.
    final AtomicReference<HashSet<Key>> autoValueIgnoredUsedInjectedKeys = new AtomicReference<>();
    // This field indicates whether this procedure is *actually* blocking based on whether a blocking operation is reachable.
    // TODO(steving) Represent this as AtomicReference<Optional<Boolean>> in order to get 3 states for the cases:
    //    1. function<foo -> bar>
    //    2. blocking function<foo -> bar>
    //    3. blocking? function<foo -> bar>
    //  For Case #3, the intention is to allow genericity over type annotations/effects/keywords. This is the same
    //  and directly inspired by Rust's proposal: https://blog.rust-lang.org/inside-rust/2022/07/27/keyword-generics.html
    //  Note that this reuse would imply that I'd need to expose the blocking state to AutoValue so that it could be
    //  used in Equality checking.
    final AtomicReference<Boolean> autoValueIgnored_IsBlocking = new AtomicReference<>(false);
    // If this procedure is marked as blocking, this field *MAY* be populated to indicate that the blocking attribute
    // is the transitive result of a dep on a downstream blocking procedure.
    final AtomicReference<HashMap<String, Type>> autoValueIgnored_BlockingProcedureDeps =
        new AtomicReference<>(new HashMap<>());
    // This field indicates whether this procedure is a graph procedure.
    final AtomicReference<Boolean> autoValueIgnored_IsGraph = new AtomicReference<>(false);
    // ---------------------- END PROCEDURE ATTRIBUTES! --------------------- //

    // TODO(steving) From Claro user perspective, there should actually be TWO DIFFERENT TYPES:
    //   1. function<foo -> bar>
    //   2. blocking function<foo -> bar>
    //  and blocking is an attribute that will have the effect of coloring all transitively dependent procedures as
    //  blocking procedures. This would enable thread safe usage of procedure references as higher order arguments to
    //  graph functions because then the Graph Function could actually validate that there is literally no usage of
    //  blocking code, and also preventing abstraction leaking via first class function support.
    public AtomicReference<Boolean> getIsBlocking() {
      return autoValueIgnored_IsBlocking;
    }

    public HashMap<String, Type> getBlockingProcedureDeps() {
      return autoValueIgnored_BlockingProcedureDeps.get();
    }

    public AtomicReference<Boolean> getIsGraph() {
      return autoValueIgnored_IsGraph;
    }

    public HashSet<Key> getUsedInjectedKeys() {
      return autoValueIgnoredUsedInjectedKeys.get();
    }

    // We need a ref to the original ProcedureDefinitionStmt for recursively asserting types to collect
    // transitively used keys.
    final AtomicReference<Stmt> autoValueIgnoredProcedureDefStmt = new AtomicReference<>();

    public Stmt getProcedureDefStmt() {
      return autoValueIgnoredProcedureDefStmt.get();
    }

    public abstract String getJavaNewTypeDefinitionStmt(
        String procedureName, StringBuilder body, Optional<StringBuilder> optionalHelperMethods);

    public abstract String getJavaNewTypeDefinitionStmtForLambda(
        String procedureName, StringBuilder body, ImmutableMap<String, Type> capturedVariables);

    public String getStaticFunctionReferenceDefinitionStmt(String procedureName) {
      return String.format(
          baseType().getJavaNewTypeStaticPreambleFormatStr(),
          procedureName,
          procedureName,
          procedureName
      );
    }

    private static final Function<ImmutableList<Type>, String> collectToArgTypesListFormatFn =
        typesByNameMap ->
            typesByNameMap.size() > 1 ?
            typesByNameMap.stream()
                .map(Type::toString)
                .collect(Collectors.joining(", ", "|", "|")) :
            typesByNameMap.stream().findFirst().map(Type::toString).get();

    @Override
    @Nullable
    public ImmutableMap<String, Type> parameterizedTypeArgs() {
      // Internal Compiler Error: method parameterizedTypeArgs() would be ambiguous for Procedure Types, defer to
      // getReturnType() or getArgTypes() as applicable instead.
      return null;
    }

    @AutoValue
    public abstract static class FunctionType extends ProcedureType {

      public static FunctionType forArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        return FunctionType.forArgsAndReturnTypes(
            argTypes, returnType, BaseType.FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, optionalActiveProcedureDefinitionTypeSupplierFn, explicitlyAnnotatedBlocking);
      }

      // Factory method for a function that takes args and returns a value.
      public static FunctionType forArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        FunctionType functionType = new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType,
            explicitlyAnnotatedBlocking
        );

        functionType.autoValueIgnoredHasArgs.set(true);
        functionType.autoValueIgnoredHasReturnValue.set(true);
        functionType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        functionType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);

        if (overrideBaseType != BaseType.FUNCTION) {
          functionType.autoValueIgnoredOptionalOverrideBaseType.set(Optional.of(overrideBaseType));
        } else {
          // Including this here for top-level function definitions instead of for lambdas since lambdas won't have
          // explicit blocking annotations (for convenience).
          functionType.getIsBlocking().set(explicitlyAnnotatedBlocking);
        }

        return functionType;
      }

      /**
       * This method exists SOLELY for representing type annotation literals provided in the source.
       */
      public static FunctionType typeLiteralForArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          boolean explicitlyAnnotatedBlocking) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        FunctionType functionType = new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType,
            explicitlyAnnotatedBlocking
        );

        functionType.autoValueIgnoredHasArgs.set(true);
        functionType.autoValueIgnoredHasReturnValue.set(true);
        // Including this here for top-level function definitions instead of for lambdas since lambdas won't have
        // explicit blocking annotations (for convenience).
        functionType.getIsBlocking().set(explicitlyAnnotatedBlocking);

        return functionType;
      }

      @Override
      public String getJavaSourceType() {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaSourceFmtStr(),
            this.getReturnType().getJavaSourceType()
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(
          String functionName, StringBuilder body, Optional<StringBuilder> optionalHelperMethods) {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaNewTypeDefinitionStmtFmtStr(),
            functionName,
            getReturnType().getJavaSourceType(),
            getJavaSourceClaroType(),
            functionName,
            functionName,
            getReturnType().getJavaSourceType(),
            body,
            optionalHelperMethods.orElse(new StringBuilder()),
            this
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmtForLambda(String functionName, StringBuilder body, ImmutableMap<String, Type> capturedVariables) {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaNewTypeDefinitionStmtFmtStr(),
            functionName,
            getReturnType().getJavaSourceType(),
            getJavaSourceClaroType(),
            functionName,
            functionName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("  private %s %s;\n", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining()),
            functionName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("%s %s", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining(", ")),
            capturedVariables.keySet().stream()
                .map(s -> String.format("    this.%s = %s;\n", s, s)).collect(Collectors.joining()),
            getReturnType().getJavaSourceType(),
            body,
            this,
            functionName,
            functionName,
            functionName,
            String.join(", ", capturedVariables.keySet())
        );
      }

      @Override
      public String toString() {
        String fmtStr;
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr = this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr();
        } else {
          fmtStr = (this.getAnnotatedBlocking() ? "blocking " : "") + this.baseType().getClaroCanonicalTypeNameFmtStr();
        }
        return String.format(
            fmtStr,
            collectToArgTypesListFormatFn.apply(this.getArgTypes()),
            this.getReturnType()
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(ImmutableList.<Type>of(%s), %s, %s)",
            this.getArgTypes().stream().map(Type::getJavaSourceClaroType).collect(Collectors.joining(", ")),
            this.getReturnType().getJavaSourceClaroType(),
            this.getIsBlocking().get()
        );
      }
    }

    @AutoValue
    public abstract static class ProviderType extends ProcedureType {
      public static ProviderType forReturnType(
          Type returnType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        return ProviderType.forReturnType(
            returnType, BaseType.PROVIDER_FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, optionalActiveProcedureDefinitionTypeSupplierFn, explicitlyAnnotatedBlocking);
      }

      public static ProviderType forReturnType(
          Type returnType,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        ProviderType providerType = new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            ImmutableList.of(),
            returnType,
            explicitlyAnnotatedBlocking
        );

        providerType.autoValueIgnoredHasArgs.set(false);
        providerType.autoValueIgnoredHasReturnValue.set(true);
        providerType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        providerType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);

        if (overrideBaseType != BaseType.PROVIDER_FUNCTION) {
          providerType.autoValueIgnoredOptionalOverrideBaseType.set(Optional.of(overrideBaseType));
        } else {
          providerType.getIsBlocking().set(explicitlyAnnotatedBlocking);
        }

        return providerType;
      }

      public static ProviderType typeLiteralForReturnType(Type returnType, boolean explicitlyAnnotatedBlocking) {
        ProviderType providerType = new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            ImmutableList.of(),
            returnType,
            explicitlyAnnotatedBlocking
        );

        providerType.autoValueIgnoredHasArgs.set(false);
        providerType.autoValueIgnoredHasReturnValue.set(true);
        providerType.getIsBlocking().set(explicitlyAnnotatedBlocking);

        return providerType;
      }

      @Override
      public String getJavaSourceType() {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaSourceFmtStr(),
            this.getReturnType().getJavaSourceType()
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(
          String providerName, StringBuilder body, Optional<StringBuilder> unusedOptionalHelperMethods) {
        String returnTypeJavaSource = getReturnType().getJavaSourceType();
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaNewTypeDefinitionStmtFmtStr(),
            providerName,
            returnTypeJavaSource,
            getJavaSourceClaroType(),
            providerName,
            providerName,
            returnTypeJavaSource,
            body,
            this,
            providerName,
            providerName,
            providerName,
            providerName
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmtForLambda(
          String providerName, StringBuilder body, ImmutableMap<String, Type> capturedVariables) {
        String returnTypeJavaSource = getReturnType().getJavaSourceType();
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .get()
                .getJavaNewTypeDefinitionStmtFmtStr(),
            providerName,
            returnTypeJavaSource,
            getJavaSourceClaroType(),
            providerName,
            providerName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("  private %s %s;\n", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining()),
            providerName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("%s %s", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining(", ")),
            capturedVariables.keySet().stream()
                .map(s -> String.format("    this.%s = %s;\n", s, s)).collect(Collectors.joining()),
            returnTypeJavaSource,
            body,
            this,
            providerName,
            providerName,
            providerName,
            String.join(", ", capturedVariables.keySet())
        );
      }

      @Override
      public String toString() {
        String fmtStr;
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr = this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr();
        } else {
          fmtStr = (this.getAnnotatedBlocking() ? "blocking " : "") + this.baseType().getClaroCanonicalTypeNameFmtStr();
        }
        return String.format(
            fmtStr,
            this.getReturnType()
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ProviderType.typeLiteralForReturnType(%s, %s)",
            this.getReturnType().getJavaSourceClaroType(),
            this.getIsBlocking().get()
        );
      }
    }

    @AutoValue
    public abstract static class ConsumerType extends ProcedureType {

      @Override
      @Nullable
      public Type getReturnType() {
        // Internal Compiler Error: Consumers do not have a return value, calling getReturnType() is invalid.
        return null;
      }

      public static ConsumerType forConsumerArgTypes(
          ImmutableList<Type> argTypes,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        return ConsumerType.forConsumerArgTypes(
            argTypes, BaseType.CONSUMER_FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, optionalActiveProcedureDefinitionTypeSupplierFn, explicitlyAnnotatedBlocking);
      }

      public static ConsumerType forConsumerArgTypes(
          ImmutableList<Type> argTypes,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Supplier<Optional<ProcedureType>> optionalActiveProcedureDefinitionTypeSupplierFn,
          boolean explicitlyAnnotatedBlocking) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            explicitlyAnnotatedBlocking
        );

        consumerType.autoValueIgnoredHasArgs.set(true);
        consumerType.autoValueIgnoredHasReturnValue.set(false);
        consumerType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        consumerType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);

        if (overrideBaseType != BaseType.CONSUMER_FUNCTION) {
          consumerType.autoValueIgnoredOptionalOverrideBaseType.set(Optional.of(overrideBaseType));
        } else {
          consumerType.getIsBlocking().set(explicitlyAnnotatedBlocking);
        }

        return consumerType;
      }

      public static ConsumerType typeLiteralForConsumerArgTypes(
          ImmutableList<Type> argTypes,
          boolean explicitlyAnnotatedBlocking) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            explicitlyAnnotatedBlocking
        );

        consumerType.autoValueIgnoredHasArgs.set(true);
        consumerType.autoValueIgnoredHasReturnValue.set(false);
        consumerType.getIsBlocking().set(explicitlyAnnotatedBlocking);

        return consumerType;
      }

      @Override
      public String getJavaSourceType() {
        return this.autoValueIgnoredOptionalOverrideBaseType.get()
            .orElse(this.baseType())
            .getJavaSourceFmtStr();
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(
          String consumerName, StringBuilder body, Optional<StringBuilder> unusedOptionalHelperMethods) {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaNewTypeDefinitionStmtFmtStr(),
            consumerName,
            getJavaSourceClaroType(),
            consumerName,
            consumerName,
            body,
            this.toString(),
            consumerName,
            consumerName,
            consumerName,
            consumerName
        );
      }

      @Override
      public String getJavaNewTypeDefinitionStmtForLambda(
          String consumerName, StringBuilder body, ImmutableMap<String, Type> capturedVariables) {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get().get().getJavaNewTypeDefinitionStmtFmtStr(),
            consumerName,
            getJavaSourceClaroType(),
            consumerName,
            consumerName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("  private %s %s;\n", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining()),
            consumerName,
            capturedVariables.entrySet().stream()
                .map(e -> String.format("%s %s", e.getValue().getJavaSourceType(), e.getKey()))
                .collect(Collectors.joining(", ")),
            capturedVariables.keySet().stream()
                .map(s -> String.format("    this.%s = %s;\n", s, s)).collect(Collectors.joining()),
            body,
            this.toString(),
            consumerName,
            consumerName,
            consumerName,
            String.join(", ", capturedVariables.keySet())
        );
      }

      @Override
      public String toString() {
        String fmtStr;
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr = this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr();
        } else {
          fmtStr = (this.getAnnotatedBlocking() ? "blocking " : "") + this.baseType().getClaroCanonicalTypeNameFmtStr();
        }
        return String.format(
            fmtStr,
            collectToArgTypesListFormatFn.apply(this.getArgTypes())
        );
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(ImmutableList.<Type>of(%s), %s)",
            this.getArgTypes().stream().map(Type::getJavaSourceClaroType).collect(Collectors.joining(", ")),
            this.getIsBlocking().get()
        );
      }
    }

    public abstract class ProcedureWrapper {
      // This is a little ridiculous, but the type safety will have to be managed exclusively by the Compiler's type
      // checking system. Stress test... Oh le do it. The given ScopedHeap is likely already the same one as given at
      // the function's definition time, but honestly just in case some weird scoping jiu-jitsu has to happen later this
      // is safer to pass in whatever ScopedHeap is necessary at call-time.
      public abstract Object apply(ImmutableList<Expr> args, ScopedHeap scopedHeap);

      public Object apply(ScopedHeap scopedHeap) {
        return apply(ImmutableList.of(), scopedHeap);
      }

      @Override
      public String toString() {
        return ProcedureType.this.toString();
      }
    }

  }

  @AutoValue
  public abstract static class FutureType extends Type {
    private static final String PARAMETERIZED_TYPE_KEY = "$value";

    public static FutureType wrapping(Type valueType) {
      return new AutoValue_Types_FutureType(BaseType.FUTURE, ImmutableMap.of(PARAMETERIZED_TYPE_KEY, valueType));
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.FutureType.wrapping(%s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY).getJavaSourceClaroType()
      );
    }
  }

  // This is gonna be used to convey to AutoValue that certain values are nullable and it will generate null-friendly
  // constructors and .equals() and .hashCode() methods.
  // https://github.com/google/auto/blob/master/value/userguide/howto.md#nullable
  @interface Nullable {
  }
}
