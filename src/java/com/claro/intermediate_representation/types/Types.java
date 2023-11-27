package com.claro.intermediate_representation.types;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos.TypeProto;
import com.claro.runtime_utilities.injector.Key;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// TODO(steving) This class needs refactoring into a standalone package.
public final class Types {
  public static final Type INTEGER = ConcreteTypes.INTEGER;
  public static final Type FLOAT = ConcreteTypes.FLOAT;
  public static final Type STRING = ConcreteTypes.STRING;
  public static final Type BOOLEAN = ConcreteTypes.BOOLEAN;
  public static final Type MODULE = ConcreteTypes.MODULE;
  public static final Type HTTP_RESPONSE = ConcreteTypes.HTTP_RESPONSE;
  public static final Type UNDECIDED = ConcreteTypes.UNDECIDED;
  public static final Type UNKNOWABLE = ConcreteTypes.UNKNOWABLE;

  // Stdlib types that get constructed via compiler intrinsics get registered here for a single source of truth.
  public static final Types.$JavaType RESOURCE_URL =
      Types.$JavaType.create(false, ImmutableList.of(), "java.net.URL");
  public static final BiFunction<String, String, Function<ScopedHeap, Types.UserDefinedType>>
      RESOURCE_TYPE_CONSTRUCTOR =
      (resourceName, resource) -> (scopedHeap) -> {
        String definingModuleDisambiguator = "stdlib$files$files";
        scopedHeap.putIdentifierValueAsTypeDef(
            String.format("%s$%s$wrappedType", resourceName, definingModuleDisambiguator),
            Types.$SyntheticOpaqueTypeWrappedValueType.create(false, RESOURCE_URL),
            resource
        );
        return Types.UserDefinedType.forTypeNameAndDisambiguator(
            "Resource", "stdlib$files$files");
      };

  public interface Collection {
    Type getElementType();
  }

  @AutoValue
  public abstract static class AtomType extends Type {
    public abstract String getName();

    public abstract String getDefiningModuleDisambiguator();

    public static AtomType forNameAndDisambiguator(String name, String definingModuleDisambiguator) {
      return new AutoValue_Types_AtomType(BaseType.ATOM, ImmutableMap.of(), name, definingModuleDisambiguator);
    }

    @Override
    public String toString() {
      return this.getName();
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.AtomType.forNameAndDisambiguator(\"%s\", \"%s\")",
          this.getName(),
          this.getDefiningModuleDisambiguator()
      );
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder().setAtom(
              TypeProtos.AtomType.newBuilder()
                  .setName(this.getName())
                  .setDefiningModuleDisambiguator(this.getDefiningModuleDisambiguator()))
          .build();
    }
  }

  @AutoValue
  public abstract static class ListType extends Type implements Collection, SupportsMutableVariant<ListType> {
    public static final String PARAMETERIZED_TYPE_KEY = "$values";

    public abstract boolean getIsMutable();

    public static ListType forValueType(Type valueType) {
      return ListType.forValueType(valueType, /*isMutable=*/false);
    }

    public static ListType forValueType(Type valueType, boolean isMutable) {
      return new AutoValue_Types_ListType(BaseType.LIST, ImmutableMap.of(PARAMETERIZED_TYPE_KEY, valueType), isMutable);
    }

    @Override
    public Type getElementType() {
      return this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY);
    }

    @Override
    public String toString() {
      String baseFormattedType = super.toString();
      return this.getIsMutable() ? "mut " + baseFormattedType : baseFormattedType;
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.ListType.forValueType(%s, %s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY).getJavaSourceClaroType(),
          this.getIsMutable()
      );
    }

    @Override
    public ListType toShallowlyMutableVariant() {
      return new AutoValue_Types_ListType(BaseType.LIST, this.parameterizedTypeArgs(), /*isMutable=*/true);
    }

    @Override
    public Optional<ListType> toDeeplyImmutableVariant() {
      Optional<? extends Type> elementType = Optional.of(getElementType());
      if (elementType.get() instanceof SupportsMutableVariant<?>) {
        elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
      } else if (elementType.get() instanceof UserDefinedType) {
        elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
      } else if (elementType.get() instanceof FutureType) {
        if (!Types.isDeeplyImmutable(
            elementType.get().parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY))) {
          // If it's not deeply immutable there's nothing that I can do to automatically make it deeply immutable
          // w/o the user manually doing a monadic(?) transform in a graph.
          elementType = Optional.empty();
        }
      }
      return elementType.map(
          type ->
              new AutoValue_Types_ListType(
                  BaseType.LIST, ImmutableMap.of(PARAMETERIZED_TYPE_KEY, type), /*isMutable=*/false));
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setList(
              TypeProtos.ListType.newBuilder()
                  .setElementType(this.getElementType().toProto())
                  .setIsMutable(this.isMutable()))
          .build();
    }
  }

  @AutoValue
  public abstract static class MapType extends Type implements SupportsMutableVariant<MapType> {
    public static final String PARAMETERIZED_TYPE_KEYS = "$keys";
    public static final String PARAMETERIZED_TYPE_VALUES = "$values";

    public abstract boolean getIsMutable();

    public static MapType forKeyValueTypes(Type keysType, Type valuesType) {
      return MapType.forKeyValueTypes(keysType, valuesType, /*isMutable=*/false);
    }

    public static MapType forKeyValueTypes(Type keysType, Type valuesType, boolean isMutable) {
      // TODO(steving) Make it illegal to declare a map wrapping future<...> keys. That's nonsensical in the sense that
      //   there's "nothing" to hash yet.
      return new AutoValue_Types_MapType(BaseType.MAP, ImmutableMap.of(PARAMETERIZED_TYPE_KEYS, keysType, PARAMETERIZED_TYPE_VALUES, valuesType), isMutable);
    }

    @Override
    public String toString() {
      String baseFormattedType = super.toString();
      return this.getIsMutable() ? "mut " + baseFormattedType : baseFormattedType;
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.MapType.forKeyValueTypes(%s, %s, %s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEYS).getJavaSourceClaroType(),
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_VALUES).getJavaSourceClaroType(),
          this.isMutable()
      );
    }

    @Override
    public MapType toShallowlyMutableVariant() {
      return new AutoValue_Types_MapType(BaseType.MAP, this.parameterizedTypeArgs(), /*isMutable=*/true);
    }

    @Override
    public Optional<MapType> toDeeplyImmutableVariant() {
      // If any of the parameterized types can't be coerced to a deeply-immutable variant then this overall type
      // instance cannot be converted to something deeply-immutable **automatically** (really I'm just saying I
      // wouldn't be able to give a good suggestion).
      ImmutableMap.Builder<String, Type> deeplyImmutableParameterizedTypeVariantsBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Type> paramTypeEntry : this.parameterizedTypeArgs().entrySet()) {
        Optional<? extends Type> elementType = Optional.of(paramTypeEntry.getValue());
        if (elementType.get() instanceof SupportsMutableVariant<?>) {
          elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof UserDefinedType) {
          elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
        } else if (
          // It'll only be possible to use futures in the values of a map, not as the keys of a map.
            paramTypeEntry.getKey().equals(MapType.PARAMETERIZED_TYPE_VALUES)
            && elementType.get() instanceof FutureType) {
          if (!Types.isDeeplyImmutable(
              elementType.get().parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY))) {
            // If it's not deeply immutable there's nothing that I can do to automatically make it deeply immutable
            // w/o the user manually doing a monadic(?) transform in a graph.
            elementType = Optional.empty();
          }
        }
        if (elementType.isPresent()) {
          deeplyImmutableParameterizedTypeVariantsBuilder.put(paramTypeEntry.getKey(), elementType.get());
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(
          new AutoValue_Types_MapType(
              BaseType.MAP,
              deeplyImmutableParameterizedTypeVariantsBuilder.build(),
              /*isMutable=*/false
          ));
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setMap(
              TypeProtos.MapType.newBuilder()
                  .setKeyType(this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEYS).toProto())
                  .setValueType(this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_VALUES).toProto())
                  .setIsMutable(this.isMutable()))
          .build();

    }
  }

  @AutoValue
  public abstract static class SetType extends Type implements SupportsMutableVariant<SetType> {
    public static final String PARAMETERIZED_TYPE = "$values";

    public abstract boolean getIsMutable();

    public static SetType forValueType(Type valueType) {
      return SetType.forValueType(valueType, /*isMutable=*/false);
    }

    public static SetType forValueType(Type valueType, boolean isMutable) {
      // TODO(steving) Make it illegal to declare a set wrapping future<...>. That's nonsensical in the sense that
      //   there's "nothing" to hash yet.
      return new AutoValue_Types_SetType(BaseType.SET, ImmutableMap.of(PARAMETERIZED_TYPE, valueType), isMutable);
    }

    @Override
    public String toString() {
      String baseFormattedType = super.toString();
      return this.getIsMutable() ? "mut " + baseFormattedType : baseFormattedType;
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.SetType.forValueType(%s, %s)",
          this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE).getJavaSourceClaroType(),
          this.isMutable()
      );
    }

    @Override
    public SetType toShallowlyMutableVariant() {
      return new AutoValue_Types_SetType(BaseType.SET, this.parameterizedTypeArgs(), /*isMutable=*/true);
    }

    @Override
    public Optional<SetType> toDeeplyImmutableVariant() {
      // If any of the parameterized types can't be coerced to a deeply-immutable variant then this overall type
      // instance cannot be converted to something deeply-immutable **automatically** (really I'm just saying I
      // wouldn't be able to give a good suggestion).
      ImmutableMap.Builder<String, Type> deeplyImmutableParameterizedTypeVariantsBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Type> paramTypeEntry : this.parameterizedTypeArgs().entrySet()) {
        Optional<? extends Type> elementType = Optional.of(paramTypeEntry.getValue());
        if (elementType.get() instanceof SupportsMutableVariant<?>) {
          elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof UserDefinedType) {
          elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
        }
        if (elementType.isPresent()) {
          deeplyImmutableParameterizedTypeVariantsBuilder.put(paramTypeEntry.getKey(), elementType.get());
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(
          new AutoValue_Types_SetType(
              BaseType.SET,
              deeplyImmutableParameterizedTypeVariantsBuilder.build(),
              /*isMutable=*/false
          ));
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setSet(
              TypeProtos.SetType.newBuilder()
                  .setElementType(this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE).toProto())
                  .setIsMutable(this.isMutable()))
          .build();
    }
  }

  @AutoValue
  public abstract static class TupleType extends Type implements Collection, SupportsMutableVariant<TupleType> {

    public abstract ImmutableList<Type> getValueTypes();

    public abstract boolean getIsMutable();

    public static TupleType forValueTypes(ImmutableList<Type> valueTypes) {
      return TupleType.forValueTypes(valueTypes, /*isMutable=*/false);
    }

    public static TupleType forValueTypes(ImmutableList<Type> valueTypes, boolean isMutable) {
      ImmutableMap.Builder<String, Type> parameterizedTypesMapBuilder = ImmutableMap.builder();
      for (int i = 0; i < valueTypes.size(); i++) {
        parameterizedTypesMapBuilder.put(String.format("$%s", i), valueTypes.get(i));
      }
      return new AutoValue_Types_TupleType(BaseType.TUPLE, parameterizedTypesMapBuilder.build(), valueTypes, isMutable);
    }

    @Override
    public Type getElementType() {
      // We literally have no way of determining this type at compile time without knowing which index is being
      // referenced so instead we'll mark this as UNDECIDED.
      return UNDECIDED;
    }

    @Override
    public String toString() {
      String baseFormattedType = String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          getValueTypes().stream().map(Type::toString).collect(Collectors.joining(", "))
      );
      return this.isMutable() ? "mut " + baseFormattedType : baseFormattedType;
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.TupleType.forValueTypes(ImmutableList.of(%s), %s)",
          Joiner.on(", ")
              .join(this.getValueTypes()
                        .stream()
                        .map(Type::getJavaSourceClaroType)
                        .collect(ImmutableList.toImmutableList())),
          this.isMutable()
      );
    }

    @Override
    public TupleType toShallowlyMutableVariant() {
      return new AutoValue_Types_TupleType(BaseType.TUPLE, this.parameterizedTypeArgs(), this.getValueTypes(), /*isMutable=*/true);
    }

    @Override
    public Optional<TupleType> toDeeplyImmutableVariant() {
      // If any of the parameterized types can't be coerced to a deeply-immutable variant then this overall type
      // instance cannot be converted to something deeply-immutable **automatically** (really I'm just saying I
      // wouldn't be able to give a good suggestion).
      ImmutableMap.Builder<String, Type> deeplyImmutableParameterizedTypeVariantsBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Type> paramTypeEntry : this.parameterizedTypeArgs().entrySet()) {
        Optional<? extends Type> elementType = Optional.of(paramTypeEntry.getValue());
        if (elementType.get() instanceof SupportsMutableVariant<?>) {
          elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof UserDefinedType) {
          elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof FutureType) {
          if (!Types.isDeeplyImmutable(
              elementType.get().parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY))) {
            // If it's not deeply immutable there's nothing that I can do to automatically make it deeply immutable
            // w/o the user manually doing a monadic(?) transform in a graph.
            elementType = Optional.empty();
          }
        }
        if (elementType.isPresent()) {
          deeplyImmutableParameterizedTypeVariantsBuilder.put(paramTypeEntry.getKey(), elementType.get());
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(
          new AutoValue_Types_TupleType(
              BaseType.TUPLE,
              deeplyImmutableParameterizedTypeVariantsBuilder.build(),
              deeplyImmutableParameterizedTypeVariantsBuilder.build()
                  .values()
                  .stream()
                  .collect(ImmutableList.toImmutableList()),
              /*isMutable=*/false
          ));
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setTuple(
              TypeProtos.TupleType.newBuilder()
                  .addAllElementTypes(
                      this.parameterizedTypeArgs().values().stream()
                          .map(Type::toProto)
                          .collect(ImmutableList.toImmutableList()))
                  .setIsMutable(this.isMutable()))
          .build();
    }
  }

  @AutoValue
  public abstract static class OneofType extends Type {

    public abstract ImmutableSet<Type> getVariantTypes();

    public static OneofType forVariantTypes(ImmutableList<Type> variants) {
      ImmutableSet<Type> variantTypesSet = ImmutableSet.copyOf(variants);
      if (variantTypesSet.size() < variants.size()) {
        // There was a duplicate type in this oneof variant list. This is an invalid instance.
        throw new RuntimeException(ClaroTypeException.forIllegalOneofTypeDeclarationWithDuplicatedTypes(variants, variantTypesSet));
      }
      return new AutoValue_Types_OneofType(BaseType.ONEOF, ImmutableMap.of(), variantTypesSet);
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          this.getVariantTypes().stream().map(Type::toString).collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.OneofType.forVariantTypes(ImmutableList.of(%s))",
          this.getVariantTypes().stream().map(Type::getJavaSourceClaroType).collect(Collectors.joining(","))
      );
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setOneof(
              TypeProtos.OneofType.newBuilder()
                  .addAllVariantTypes(
                      this.getVariantTypes().stream()
                          .map(Type::toProto)
                          .collect(ImmutableList.toImmutableList())))
          .build();
    }
  }

  @AutoValue
  public abstract static class StructType extends Type implements SupportsMutableVariant<StructType> {
    // Instead of using the parameterizedTypesMap I unfortunately have to explicitly list them separately as parallel
    // lists literally just so that the equals() and hashcode() impls correctly distinguish btwn field orderings which
    // ImmutableMap doesn't.
    public abstract ImmutableList<String> getFieldNames();

    public abstract ImmutableList<Type> getFieldTypes();

    abstract boolean getIsMutable();

    public static StructType forFieldTypes(ImmutableList<String> fieldNames, ImmutableList<Type> fieldTypes, boolean isMutable) {
      return new AutoValue_Types_StructType(BaseType.STRUCT, ImmutableMap.of(), fieldNames, fieldTypes, isMutable);
    }

    @Override
    public String toString() {
      String baseFormattedTypeStr =
          String.format(
              this.baseType().getClaroCanonicalTypeNameFmtStr(),
              IntStream.range(0, this.getFieldNames().size()).boxed()
                  .map(i ->
                           String.format("%s: %s", this.getFieldNames().get(i), this.getFieldTypes().get(i)))
                  .collect(Collectors.joining(", "))
          );
      return this.getIsMutable() ? "mut " + baseFormattedTypeStr : baseFormattedTypeStr;
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.StructType.forFieldTypes(ImmutableList.of(%s), ImmutableList.of(%s), %s)",
          this.getFieldNames().stream().map(n -> String.format("\"%s\"", n)).collect(Collectors.joining(", ")),
          this.getFieldTypes().stream().map(Type::getJavaSourceClaroType).collect(Collectors.joining(", ")),
          this.getIsMutable()
      );
    }

    @Override
    public StructType toShallowlyMutableVariant() {
      return StructType.forFieldTypes(this.getFieldNames(), this.getFieldTypes(), /*isMutable=*/true);
    }

    @Override
    public Optional<StructType> toDeeplyImmutableVariant() {
      // If any of the parameterized types can't be coerced to a deeply-immutable variant then this overall type
      // instance cannot be converted to something deeply-immutable **automatically** (really I'm just saying I
      // wouldn't be able to give a good suggestion).
      ImmutableList.Builder<Type> deeplyImmutableParameterizedTypeVariantsBuilder = ImmutableList.builder();
      for (Map.Entry<String, Type> paramTypeEntry : this.parameterizedTypeArgs().entrySet()) {
        Optional<? extends Type> elementType = Optional.of(paramTypeEntry.getValue());
        if (elementType.get() instanceof SupportsMutableVariant<?>) {
          elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof UserDefinedType) {
          elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof FutureType) {
          if (!Types.isDeeplyImmutable(
              elementType.get().parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY))) {
            // If it's not deeply immutable there's nothing that I can do to automatically make it deeply immutable
            // w/o the user manually doing a monadic(?) transform in a graph.
            elementType = Optional.empty();
          }
        }
        if (elementType.isPresent()) {
          deeplyImmutableParameterizedTypeVariantsBuilder.add(elementType.get());
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(
          StructType.forFieldTypes(
              this.getFieldNames(),
              deeplyImmutableParameterizedTypeVariantsBuilder.build(),
              /*isMutable=*/false
          ));
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setStruct(
              TypeProtos.StructType.newBuilder()
                  .addAllFieldNames(this.getFieldNames())
                  .addAllFieldTypes(
                      this.getFieldTypes().stream()
                          .map(Type::toProto)
                          .collect(ImmutableList.toImmutableList()))
                  .setIsMutable(this.isMutable()))
          .build();
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
    // For the sake of leveraging this field for blocking? as well, null == blocking?.
    @Types.Nullable
    public abstract Boolean getAnnotatedBlocking();

    // This field determines whether this procedure is generic over the blocking keyword, meaning that this type
    // definition is actually something like a template rather than a concrete type to type check against at the call
    // site. Instead, at the call-site, should check for an appropriate concrete type.
    public abstract Optional<ImmutableSet<Integer>> getAnnotatedBlockingGenericOverArgs();

    // In case this is a generic procedure, indicate the names of the generic args here.
    public abstract Optional<ImmutableList<String>> getGenericProcedureArgNames();

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
    // This field is mutable specifically because we need to be able to update this mapping first with all
    // contract impls that are directly required by this procedure, and then by all of its transitively
    // required contract impls. This is done in multiple phases, because we're parsing a DAG in linear order.
    public final AtomicReference<ArrayListMultimap<String, ImmutableList<Type>>>
        allTransitivelyRequiredContractNamesToGenericArgs = new AtomicReference<>();
    // This field indicates whether this procedure is *actually* blocking based on whether a blocking operation is reachable.
    final AtomicReference<Boolean> autoValueIgnored_IsBlocking = new AtomicReference<>(false);
    // If this procedure is marked as blocking, this field *MAY* be populated to indicate that the blocking attribute
    // is the transitive result of a dep on a downstream blocking procedure.
    final AtomicReference<HashMap<String, Type>> autoValueIgnored_BlockingProcedureDeps =
        new AtomicReference<>(new HashMap<>());
    // This field indicates whether this procedure is a graph procedure.
    final AtomicReference<Boolean> autoValueIgnored_IsGraph = new AtomicReference<>(false);
    // ---------------------- END PROCEDURE ATTRIBUTES! --------------------- //

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

    public ArrayListMultimap<String, ImmutableList<Type>> getAllTransitivelyRequiredContractNamesToGenericArgs() {
      return allTransitivelyRequiredContractNamesToGenericArgs.get();
    }

    // We need a ref to the original ProcedureDefinitionStmt (or GenericFunctionDefinitionStmt in the case of a generic
    // procedure) for recursively asserting types to collect transitively used keys.
    public final AtomicReference<Stmt> autoValueIgnoredProcedureDefStmt = new AtomicReference<>();

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
          boolean explicitlyAnnotatedBlocking) {
        return FunctionType.forArgsAndReturnTypes(
            argTypes, returnType, BaseType.FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, explicitlyAnnotatedBlocking, Optional
                .empty());
      }

      // Factory method for a function that takes args and returns a value.
      public static FunctionType forArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> genericBlockingOnArgs) {
        return FunctionType.forArgsAndReturnTypes(argTypes, returnType, overrideBaseType, directUsedInjectedKeys, procedureDefinitionStmt, explicitlyAnnotatedBlocking, genericBlockingOnArgs, Optional
            .empty(), Optional.empty());
      }

      // Factory method for a function that takes args and returns a value.
      public static FunctionType forArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> genericBlockingOnArgs,
          Optional<ImmutableList<String>> optionalGenericProcedureArgNames,
          Optional<ImmutableListMultimap<String, ImmutableList<Type>>> optionalRequiredContractNamesToGenericArgs) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        FunctionType functionType = new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType,
            explicitlyAnnotatedBlocking,
            genericBlockingOnArgs,
            optionalGenericProcedureArgNames
        );

        functionType.autoValueIgnoredHasArgs.set(true);
        functionType.autoValueIgnoredHasReturnValue.set(true);
        functionType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        functionType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);
        functionType.allTransitivelyRequiredContractNamesToGenericArgs.set(
            optionalRequiredContractNamesToGenericArgs.map(ArrayListMultimap::create).orElse(null));

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
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> optionalAnnotatedBlockingGenericOverArgs,
          Optional<ImmutableList<String>> optionalGenericProcedureArgNames) {
        // Inheritance has gotten out of hand yet again.... FunctionType doesn't fit within the mold and won't have a
        // parameterizedTypeArgs map used
        FunctionType functionType = new AutoValue_Types_ProcedureType_FunctionType(
            BaseType.FUNCTION,
            argTypes,
            returnType,
            explicitlyAnnotatedBlocking,
            optionalAnnotatedBlockingGenericOverArgs,
            optionalGenericProcedureArgNames
        );

        functionType.autoValueIgnoredHasArgs.set(true);
        functionType.autoValueIgnoredHasReturnValue.set(true);
        // Including this here for top-level function definitions instead of for lambdas since lambdas won't have
        // explicit blocking annotations (for convenience).
        functionType.getIsBlocking().set(explicitlyAnnotatedBlocking);

        return functionType;
      }

      public static FunctionType typeLiteralForArgsAndReturnTypes(
          ImmutableList<Type> argTypes,
          Type returnType,
          Boolean explicitlyAnnotatedBlocking) {
        return typeLiteralForArgsAndReturnTypes(argTypes, returnType, explicitlyAnnotatedBlocking, Optional.empty(), Optional.empty());
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
        StringBuilder fmtStr = new StringBuilder();
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr.append(this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr());
        } else {
          if (this.getAnnotatedBlocking() == null) {
            fmtStr.append("blocking");
            fmtStr.append(
                this.getAnnotatedBlockingGenericOverArgs().isPresent()
                ? this.getAnnotatedBlockingGenericOverArgs().get().stream()
                    .map(i -> Integer.toString(i))
                    .collect(Collectors.joining("|", ":", " "))
                : "? ");
          } else {
            fmtStr.append(this.getAnnotatedBlocking() ? "blocking " : "");
          }
          fmtStr.append(this.baseType().getClaroCanonicalTypeNameFmtStr());
        }
        return String.format(
            fmtStr.toString(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes()),
            this.getReturnType(),
            this.getGenericProcedureArgNames()
                .map(
                    genArgNames ->
                        genArgNames.stream()
                            // First convert the name to a $GenericTypeParam because the toString has been overridden.
                            .map(genArgName -> Types.$GenericTypeParam.forTypeParamName(genArgName).toString())
                            .collect(Collectors.joining(", ", " Generic Over {", "}")))
                .orElse("")
            + Optional.ofNullable(this.getAllTransitivelyRequiredContractNamesToGenericArgs())
                .map(requiredContracts ->
                         requiredContracts.entries().stream()
                             .map(entry ->
                                      String.format(
                                          "%s<%s>",
                                          entry.getKey(),
                                          entry.getValue()
                                              .stream()
                                              .map(Type::toString)
                                              .collect(Collectors.joining(", "))
                                      ))
                             .collect(Collectors.joining(", ", " Requiring Impls for Contracts {", "}")))
                .orElse("")
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

      @Override
      public TypeProto toProto() {
        TypeProtos.FunctionType.Builder functionTypeBuilder =
            TypeProtos.FunctionType.newBuilder()
                .addAllArgTypes(this.getArgTypes()
                                    .stream()
                                    .map(Type::toProto)
                                    .collect(ImmutableList.toImmutableList()))
                .setOutputType(this.getReturnType().toProto())
                .setAnnotatedBlocking(ProcedureType.getProtoBlockingAnnotation(this.getAnnotatedBlocking()));
        if (this.getAnnotatedBlockingGenericOverArgs().isPresent()) {
          functionTypeBuilder.addAllOptionalAnnotatedBlockingGenericOverArgs(
              this.getAnnotatedBlockingGenericOverArgs().get());
        }
        if (this.getGenericProcedureArgNames().isPresent()) {
          functionTypeBuilder.addAllOptionalGenericTypeParamNames(this.getGenericProcedureArgNames().get());
        }
        if (Optional.ofNullable(this.allTransitivelyRequiredContractNamesToGenericArgs.get()).isPresent()) {
          functionTypeBuilder.addAllRequiredContracts(
              this.allTransitivelyRequiredContractNamesToGenericArgs.get().entries().stream()
                  .map(e -> {
                    int moduleDisambiguatorSplit = e.getKey().indexOf('$');
                    TypeProtos.RequiredContract.Builder res = TypeProtos.RequiredContract.newBuilder();
                    if (moduleDisambiguatorSplit == -1) {
                      // The contract was defined in the current compilation unit. No disambiguator to split.
                      res.setName(e.getKey())
                          .setDefiningModuleDisambiguator(ScopedHeap.getDefiningModuleDisambiguator(
                              Optional.empty()));
                    } else {
                      // The contract was defined in another compilation unit. Split format
                      // `ContractName$some$arbitrary$disambiguator$module` dropping extra '$' separator.
                      res.setName(e.getKey().substring(0, moduleDisambiguatorSplit))
                          .setDefiningModuleDisambiguator(e.getKey().substring(moduleDisambiguatorSplit + 1));
                    }
                    res.addAllGenericTypeParams(
                        e.getValue().stream()
                            .map(t -> (($GenericTypeParam) t).getTypeParamName())
                            .collect(Collectors.toList()));
                    return res.build();
                  })
                  .collect(Collectors.toList())
          );
        }
        return TypeProto.newBuilder().setFunction(functionTypeBuilder).build();
      }
    }

    @AutoValue
    public abstract static class ProviderType extends ProcedureType {
      public static ProviderType forReturnType(
          Type returnType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          boolean explicitlyAnnotatedBlocking) {
        return ProviderType.forReturnType(
            returnType, BaseType.PROVIDER_FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, explicitlyAnnotatedBlocking, Optional.empty(), Optional.empty());
      }

      public static ProviderType forReturnType(
          Type returnType,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableList<String>> optionalGenericProcedureArgNames,
          Optional<ImmutableListMultimap<String, ImmutableList<Type>>> optionalRequiredContractNamesToGenericArgs) {
        ProviderType providerType = new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            ImmutableList.of(),
            returnType,
            explicitlyAnnotatedBlocking,
            Optional.empty(),
            optionalGenericProcedureArgNames
        );

        providerType.autoValueIgnoredHasArgs.set(false);
        providerType.autoValueIgnoredHasReturnValue.set(true);
        providerType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        providerType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);
        providerType.allTransitivelyRequiredContractNamesToGenericArgs.set(
            optionalRequiredContractNamesToGenericArgs.map(ArrayListMultimap::create).orElse(null));

        if (overrideBaseType != BaseType.PROVIDER_FUNCTION) {
          providerType.autoValueIgnoredOptionalOverrideBaseType.set(Optional.of(overrideBaseType));
        } else {
          providerType.getIsBlocking().set(explicitlyAnnotatedBlocking);
        }

        return providerType;
      }

      public static ProviderType typeLiteralForReturnType(Type returnType, Boolean explicitlyAnnotatedBlocking) {
        ProviderType providerType = new AutoValue_Types_ProcedureType_ProviderType(
            BaseType.PROVIDER_FUNCTION,
            ImmutableList.of(),
            returnType,
            explicitlyAnnotatedBlocking,
            Optional.empty(),
            Optional.empty()
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
          String providerName, StringBuilder body, Optional<StringBuilder> optionalHelperMethods) {
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
            optionalHelperMethods.orElse(new StringBuilder()),
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
        StringBuilder fmtStr = new StringBuilder();
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr.append(this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr());
        } else {
          if (this.getAnnotatedBlocking() == null) {
            fmtStr.append("blocking");
            fmtStr.append(
                this.getAnnotatedBlockingGenericOverArgs().isPresent()
                ? this.getAnnotatedBlockingGenericOverArgs().get().stream()
                    .map(i -> Integer.toString(i))
                    .collect(Collectors.joining("|", ":", " "))
                : "? ");
          } else {
            fmtStr.append(this.getAnnotatedBlocking() ? "blocking " : "");
          }
          fmtStr.append(this.baseType().getClaroCanonicalTypeNameFmtStr());
        }
        return String.format(
            fmtStr.toString(),
            this.getReturnType()
        ) +
               this.getGenericProcedureArgNames()
                   .map(
                       genArgNames ->
                           genArgNames.stream()
                               // First convert the name to a $GenericTypeParam because the toString has been overridden.
                               .map(genArgName -> Types.$GenericTypeParam.forTypeParamName(genArgName).toString())
                               .collect(Collectors.joining(", ", " Generic Over {", "}")))
                   .orElse("")
               + Optional.ofNullable(this.getAllTransitivelyRequiredContractNamesToGenericArgs())
                   .map(requiredContracts ->
                            requiredContracts.entries().stream()
                                .map(entry ->
                                         String.format(
                                             "%s<%s>",
                                             entry.getKey(),
                                             entry.getValue()
                                                 .stream()
                                                 .map(Type::toString)
                                                 .collect(Collectors.joining(", "))
                                         ))
                                .collect(Collectors.joining(", ", " Requiring Impls for Contracts {", "}")))
                   .orElse("");
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ProviderType.typeLiteralForReturnType(%s, %s)",
            this.getReturnType().getJavaSourceClaroType(),
            this.getIsBlocking().get()
        );
      }

      @Override
      public TypeProto toProto() {
        TypeProtos.ProviderType.Builder providerTypeBuilder =
            TypeProtos.ProviderType.newBuilder()
                .setOutputType(this.getReturnType().toProto())
                .setAnnotatedBlocking(ProcedureType.getProtoBlockingAnnotation(this.getAnnotatedBlocking()));
        if (this.getGenericProcedureArgNames().isPresent()) {
          providerTypeBuilder.addAllOptionalGenericTypeParamNames(this.getGenericProcedureArgNames().get());
        }
        if (Optional.ofNullable(this.allTransitivelyRequiredContractNamesToGenericArgs.get()).isPresent()) {
          providerTypeBuilder.addAllRequiredContracts(
              this.allTransitivelyRequiredContractNamesToGenericArgs.get().entries().stream()
                  .map(e -> {
                    int moduleDisambiguatorSplit = e.getKey().indexOf('$');
                    TypeProtos.RequiredContract.Builder res = TypeProtos.RequiredContract.newBuilder();
                    if (moduleDisambiguatorSplit == -1) {
                      // The contract was defined in the current compilation unit. No disambiguator to split.
                      res.setName(e.getKey())
                          .setDefiningModuleDisambiguator(ScopedHeap.getDefiningModuleDisambiguator(
                              Optional.empty()));
                    } else {
                      // The contract was defined in another compilation unit. Split format
                      // `ContractName$some$arbitrary$disambiguator$module` dropping extra '$' separator.
                      res.setName(e.getKey().substring(0, moduleDisambiguatorSplit))
                          .setDefiningModuleDisambiguator(e.getKey().substring(moduleDisambiguatorSplit + 1));
                    }
                    res.addAllGenericTypeParams(
                        e.getValue().stream()
                            .map(t -> (($GenericTypeParam) t).getTypeParamName())
                            .collect(Collectors.toList()));
                    return res.build();
                  })
                  .collect(Collectors.toList())
          );
        }
        return TypeProto.newBuilder().setProvider(providerTypeBuilder).build();
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
          boolean explicitlyAnnotatedBlocking) {
        return ConsumerType.forConsumerArgTypes(
            argTypes, BaseType.CONSUMER_FUNCTION, directUsedInjectedKeys, procedureDefinitionStmt, explicitlyAnnotatedBlocking, Optional
                .empty());
      }

      public static ConsumerType forConsumerArgTypes(
          ImmutableList<Type> argTypes,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> genericBlockingOnArgs) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            explicitlyAnnotatedBlocking,
            genericBlockingOnArgs,
            Optional.empty() // TODO(steving) Implement generic consumer functions.
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

      public static ConsumerType forConsumerArgTypes(
          ImmutableList<Type> argTypes,
          BaseType overrideBaseType,
          Set<Key> directUsedInjectedKeys,
          Stmt procedureDefinitionStmt,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> genericBlockingOnArgs,
          Optional<ImmutableList<String>> optionalGenericProcedureArgNames,
          Optional<ImmutableListMultimap<String, ImmutableList<Type>>> optionalRequiredContractNamesToGenericArgs) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            explicitlyAnnotatedBlocking,
            genericBlockingOnArgs,
            optionalGenericProcedureArgNames
        );

        consumerType.autoValueIgnoredHasArgs.set(true);
        consumerType.autoValueIgnoredHasReturnValue.set(false);
        consumerType.autoValueIgnoredUsedInjectedKeys.set(Sets.newHashSet(directUsedInjectedKeys));
        consumerType.autoValueIgnoredProcedureDefStmt.set(procedureDefinitionStmt);
        consumerType.allTransitivelyRequiredContractNamesToGenericArgs.set(
            optionalRequiredContractNamesToGenericArgs.map(ArrayListMultimap::create).orElse(null));

        if (overrideBaseType != BaseType.CONSUMER_FUNCTION) {
          consumerType.autoValueIgnoredOptionalOverrideBaseType.set(Optional.of(overrideBaseType));
        } else {
          consumerType.getIsBlocking().set(explicitlyAnnotatedBlocking);
        }

        return consumerType;
      }

      public static ConsumerType typeLiteralForConsumerArgTypes(
          ImmutableList<Type> argTypes,
          Boolean explicitlyAnnotatedBlocking,
          Optional<ImmutableSet<Integer>> optionalAnnotatedBlockingGenericOverArgs,
          Optional<ImmutableList<String>> optionalGenericProcedureArgNames) {
        ConsumerType consumerType = new AutoValue_Types_ProcedureType_ConsumerType(
            BaseType.CONSUMER_FUNCTION,
            argTypes,
            explicitlyAnnotatedBlocking,
            optionalAnnotatedBlockingGenericOverArgs,
            optionalGenericProcedureArgNames
        );

        consumerType.autoValueIgnoredHasArgs.set(true);
        consumerType.autoValueIgnoredHasReturnValue.set(false);
        consumerType.getIsBlocking().set(explicitlyAnnotatedBlocking);

        return consumerType;
      }

      public static ConsumerType typeLiteralForConsumerArgTypes(
          ImmutableList<Type> argTypes,
          Boolean explicitlyAnnotatedBlocking) {
        return typeLiteralForConsumerArgTypes(argTypes, explicitlyAnnotatedBlocking, Optional.empty(), Optional.empty());
      }

      @Override
      public String getJavaSourceType() {
        return this.autoValueIgnoredOptionalOverrideBaseType.get()
            .orElse(this.baseType())
            .getJavaSourceFmtStr();
      }

      @Override
      public String getJavaNewTypeDefinitionStmt(
          String consumerName, StringBuilder body, Optional<StringBuilder> optionalHelperMethods) {
        return String.format(
            this.autoValueIgnoredOptionalOverrideBaseType.get()
                .orElse(this.baseType())
                .getJavaNewTypeDefinitionStmtFmtStr(),
            consumerName,
            getJavaSourceClaroType(),
            consumerName,
            consumerName,
            body,
            optionalHelperMethods.orElse(new StringBuilder()),
            this,
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
        StringBuilder fmtStr = new StringBuilder();
        if (this.autoValueIgnoredOptionalOverrideBaseType.get().isPresent()) {
          fmtStr.append(this.autoValueIgnoredOptionalOverrideBaseType.get().get().getClaroCanonicalTypeNameFmtStr());
        } else {
          if (this.getAnnotatedBlocking() == null) {
            fmtStr.append("blocking");
            fmtStr.append(
                this.getAnnotatedBlockingGenericOverArgs().isPresent()
                ? this.getAnnotatedBlockingGenericOverArgs().get().stream()
                    .map(i -> Integer.toString(i))
                    .collect(Collectors.joining("|", ":", " "))
                : "? ");
          } else {
            fmtStr.append(this.getAnnotatedBlocking() ? "blocking " : "");
          }
          fmtStr.append(this.baseType().getClaroCanonicalTypeNameFmtStr());
        }
        return String.format(
            fmtStr.toString(),
            collectToArgTypesListFormatFn.apply(this.getArgTypes())
        ) +
               this.getGenericProcedureArgNames()
                   .map(
                       genArgNames ->
                           genArgNames.stream()
                               // First convert the name to a $GenericTypeParam because the toString has been overridden.
                               .map(genArgName -> Types.$GenericTypeParam.forTypeParamName(genArgName).toString())
                               .collect(Collectors.joining(", ", " Generic Over {", "}")))
                   .orElse("")
               + Optional.ofNullable(this.getAllTransitivelyRequiredContractNamesToGenericArgs())
                   .map(requiredContracts ->
                            requiredContracts.entries().stream()
                                .map(entry ->
                                         String.format(
                                             "%s<%s>",
                                             entry.getKey(),
                                             entry.getValue()
                                                 .stream()
                                                 .map(Type::toString)
                                                 .collect(Collectors.joining(", "))
                                         ))
                                .collect(Collectors.joining(", ", " Requiring Impls for Contracts {", "}")))
                   .orElse("");
      }

      @Override
      public String getJavaSourceClaroType() {
        return String.format(
            "Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(ImmutableList.<Type>of(%s), %s)",
            this.getArgTypes().stream().map(Type::getJavaSourceClaroType).collect(Collectors.joining(", ")),
            this.getIsBlocking().get()
        );
      }

      @Override
      public TypeProto toProto() {
        TypeProtos.ConsumerType.Builder consumerTypeBuilder =
            TypeProtos.ConsumerType.newBuilder()
                .addAllArgTypes(
                    this.getArgTypes().stream().map(Type::toProto).collect(ImmutableList.toImmutableList()))
                .setAnnotatedBlocking(ProcedureType.getProtoBlockingAnnotation(this.getAnnotatedBlocking()));
        if (this.getAnnotatedBlockingGenericOverArgs().isPresent()) {
          consumerTypeBuilder.addAllOptionalAnnotatedBlockingGenericOverArgs(
              this.getAnnotatedBlockingGenericOverArgs().get());
        }
        if (this.getGenericProcedureArgNames().isPresent()) {
          consumerTypeBuilder.addAllOptionalGenericTypeParamNames(this.getGenericProcedureArgNames().get());
        }
        if (Optional.ofNullable(this.allTransitivelyRequiredContractNamesToGenericArgs.get()).isPresent()) {
          consumerTypeBuilder.addAllRequiredContracts(
              this.allTransitivelyRequiredContractNamesToGenericArgs.get().entries().stream()
                  .map(e -> {
                    int moduleDisambiguatorSplit = e.getKey().indexOf('$');
                    TypeProtos.RequiredContract.Builder res = TypeProtos.RequiredContract.newBuilder();
                    if (moduleDisambiguatorSplit == -1) {
                      // The contract was defined in the current compilation unit. No disambiguator to split.
                      res.setName(e.getKey())
                          .setDefiningModuleDisambiguator(ScopedHeap.getDefiningModuleDisambiguator(
                              Optional.empty()));
                    } else {
                      // The contract was defined in another compilation unit. Split format
                      // `ContractName$some$arbitrary$disambiguator$module` dropping extra '$' separator.
                      res.setName(e.getKey().substring(0, moduleDisambiguatorSplit))
                          .setDefiningModuleDisambiguator(e.getKey().substring(moduleDisambiguatorSplit + 1));
                    }
                    res.addAllGenericTypeParams(
                        e.getValue().stream()
                            .map(t -> (($GenericTypeParam) t).getTypeParamName())
                            .collect(Collectors.toList()));
                    return res.build();
                  })
                  .collect(Collectors.toList())
          );
        }
        return TypeProto.newBuilder().setConsumer(consumerTypeBuilder).build();
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

    static TypeProtos.ProcedureBlockingAnnotation getProtoBlockingAnnotation(Boolean blockingAnnotation) {
      if (blockingAnnotation == null) {
        return TypeProtos.ProcedureBlockingAnnotation.BLOCKING_GENERIC;
      } else if (blockingAnnotation) {
        return TypeProtos.ProcedureBlockingAnnotation.BLOCKING;
      } else {
        return TypeProtos.ProcedureBlockingAnnotation.NON_BLOCKING;
      }
    }

    static Boolean getBlockingAnnotationFromProto(TypeProtos.ProcedureBlockingAnnotation blockingAnnotation) {
      switch (blockingAnnotation) {
        case NON_BLOCKING:
          return false;
        case BLOCKING:
          return true;
        case BLOCKING_GENERIC:
          return null;
        default:
          throw new RuntimeException(
              "Internal Compiler Error! Encountered unknown procedure blocking annotation while parsing from proto: " +
              blockingAnnotation);
      }
    }
  }

  @AutoValue
  public abstract static class FutureType extends Type {
    public static final String PARAMETERIZED_TYPE_KEY = "$value";

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

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setFuture(
              TypeProtos.FutureType.newBuilder()
                  .setWrappedType(this.parameterizedTypeArgs().get(PARAMETERIZED_TYPE_KEY).toProto()))
          .build();
    }
  }

  @AutoValue
  public abstract static class UserDefinedType extends Type {
    public static final HashMap<String, Type> $resolvedWrappedTypes = Maps.newHashMap();
    public static final HashMap<String, ImmutableList<String>> $typeParamNames = Maps.newHashMap();

    public abstract String getTypeName();

    // This disambiguator is necessary in order for two same-named types defined in separate Modules to have types
    // correctly considered to be distinct.
    public abstract String getDefiningModuleDisambiguator();

    public static UserDefinedType forTypeNameAndDisambiguator(String typeName, String definingModuleDisambiguator) {
      return new AutoValue_Types_UserDefinedType(BaseType.USER_DEFINED_TYPE, ImmutableMap.of(), typeName, definingModuleDisambiguator);
    }

    public static UserDefinedType forTypeNameAndParameterizedTypes(
        String typeName, String definingModuleDisambiguator, ImmutableList<Type> parameterizedTypes) {
      return new AutoValue_Types_UserDefinedType(
          BaseType.USER_DEFINED_TYPE,
          IntStream.range(0, parameterizedTypes.size()).boxed()
              .collect(ImmutableMap.<Integer, String, Type>toImmutableMap(Object::toString, parameterizedTypes::get)),
          typeName,
          definingModuleDisambiguator
      );
    }

    @Override
    public String getJavaSourceClaroType() {
      if (this.parameterizedTypeArgs().isEmpty()) {
        return String.format(
            "Types.UserDefinedType.forTypeNameAndDisambiguator(\"%s\", \"%s\")",
            this.getTypeName(),
            this.getDefiningModuleDisambiguator()
        );
      }
      return String.format(
          "Types.UserDefinedType.forTypeNameAndParameterizedTypes(\"%s\", \"%s\", ImmutableList.of(%s))",
          this.getTypeName(),
          this.getDefiningModuleDisambiguator(),
          this.parameterizedTypeArgs()
              .values()
              .stream()
              .map(Type::getJavaSourceClaroType)
              .collect(Collectors.joining(", "))
      );
    }

    @Override
    public String getJavaSourceType() {
      // Setup $GenericTypeParam to defer codegen to the concrete types for this instance if this one is parameterized.
      Optional<Map<Type, Type>> originalGenTypeCodegenMappings
          = $GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen;
      if (!this.parameterizedTypeArgs().isEmpty()) {
        ImmutableList<String> typeParamNames = UserDefinedType.$typeParamNames.get(
            String.format("%s$%s", this.getTypeName(), this.getDefiningModuleDisambiguator()));
        $GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen =
            Optional.of(
                IntStream.range(0, this.parameterizedTypeArgs().size()).boxed()
                    .collect(ImmutableMap.toImmutableMap(
                        i -> $GenericTypeParam.forTypeParamName(typeParamNames.get(i)),
                        i -> this.parameterizedTypeArgs().get(i.toString())
                    )));
      }

      // Actual codegen.
      String res = String.format(
          this.baseType().getJavaSourceFmtStr(),
          UserDefinedType.$resolvedWrappedTypes.get(
                  String.format("%s$%s", this.getTypeName(), this.getDefiningModuleDisambiguator()))
              .getJavaSourceType()
      );

      // Reset state.
      if (!this.parameterizedTypeArgs().isEmpty()) {
        $GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen = originalGenTypeCodegenMappings;
      }
      return res;
    }

    @Override
    public String toString() {
      String disambig = this.getDefiningModuleDisambiguator();
      int i = disambig.lastIndexOf('$');
      String namespace =
          disambig.isEmpty()
          ? ""
          : String.format(
              "[module at //%s:%s]::",
              disambig.substring(0, i).replaceAll("\\$", "/"),
              disambig.substring(i + 1)
          );
      if (this.parameterizedTypeArgs().isEmpty()) {
        return namespace + this.getTypeName();
      }
      return String.format(
          "%s%s<%s>",
          namespace,
          this.getTypeName(),
          this.parameterizedTypeArgs().values().stream().map(Type::toString).collect(Collectors.joining(", "))
      );
    }

    // It's not always the case that all arbitrary user-defined types have a natural deeply-immutable variant. In
    // particular, if the wrapped type itself already contains an explicit `mut` annotation, then it's impossible
    // to construct any instance of this type that is deeply-immutable.
    public Optional<UserDefinedType> toDeeplyImmutableVariant() {
      if (!Types.isDeeplyImmutable(
          UserDefinedType.$resolvedWrappedTypes
              .get(String.format("%s$%s", getTypeName(), getDefiningModuleDisambiguator())))) {
        return Optional.empty();
      }
      // If any of the parameterized types can't be coerced to a deeply-immutable variant then this overall type
      // instance cannot be converted to something deeply-immutable **automatically** (really I'm just saying I
      // wouldn't be able to give a good suggestion).
      ImmutableMap.Builder<String, Type> deeplyImmutableParameterizedTypeVariantsBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Type> paramTypeEntry : this.parameterizedTypeArgs().entrySet()) {
        Optional<? extends Type> elementType = Optional.of(paramTypeEntry.getValue());
        if (elementType.get() instanceof SupportsMutableVariant<?>) {
          elementType = ((SupportsMutableVariant<?>) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof UserDefinedType) {
          elementType = ((UserDefinedType) elementType.get()).toDeeplyImmutableVariant();
        } else if (elementType.get() instanceof FutureType) {
          if (!Types.isDeeplyImmutable(
              elementType.get().parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY))) {
            // If it's not deeply immutable there's nothing that I can do to automatically make it deeply immutable
            // w/o the user manually doing a monadic(?) transform in a graph.
            elementType = Optional.empty();
          }
        }
        if (elementType.isPresent()) {
          deeplyImmutableParameterizedTypeVariantsBuilder.put(paramTypeEntry.getKey(), elementType.get());
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(
          new AutoValue_Types_UserDefinedType(
              BaseType.USER_DEFINED_TYPE,
              deeplyImmutableParameterizedTypeVariantsBuilder.build(),
              this.getTypeName(),
              this.getDefiningModuleDisambiguator()
          ));
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setUserDefinedType(
              TypeProtos.UserDefinedType.newBuilder()
                  .setTypeName(this.getTypeName())
                  .setDefiningModuleDisambiguator(this.getDefiningModuleDisambiguator())
                  .addAllParameterizedTypes(
                      this.parameterizedTypeArgs().values().stream()
                          .map(Type::toProto)
                          .collect(ImmutableList.toImmutableList())))
          .build();
    }
  }

  // This is really a meta-type which exists primarily to allow some mechanism of holding the name of a generic
  // type param in the scopedheap. This should see very limited use throughout the compiler internals only and
  // none at all by Claro programs.
  @AutoValue
  public abstract static class $GenericTypeParam extends Type {
    public static Optional<Map<Type, Type>> concreteTypeMappingsForBetterErrorMessages = Optional.empty();
    public static Optional<Map<Type, Type>> concreteTypeMappingsForParameterizedTypeCodegen = Optional.empty();

    public abstract String getTypeParamName();

    public static $GenericTypeParam forTypeParamName(String name) {
      return new AutoValue_Types_$GenericTypeParam(BaseType.$GENERIC_TYPE_PARAM, ImmutableMap.of(), name);
    }

    @Override
    public String getJavaSourceClaroType() {
      // In the case that we're doing codegen for a parameterized type, we can conveniently redirect to the concrete
      // types' codegen so that we don't need to do structural pattern matching just for codegen.
      if ($GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen.isPresent()) {
        return concreteTypeMappingsForParameterizedTypeCodegen.get().get(this).getJavaSourceClaroType();
      }
      throw new ClaroParserException("Internal Compiler Error: This type should be unreachable in Claro programs.");
    }

    @Override
    public String getJavaSourceType() {
      // In the case that we're doing codegen for a parameterized type, we can conveniently redirect to the concrete
      // types' codegen so that we don't need to do structural pattern matching just for codegen.
      if ($GenericTypeParam.concreteTypeMappingsForParameterizedTypeCodegen.isPresent()) {
        return concreteTypeMappingsForParameterizedTypeCodegen.get().get(this).getJavaSourceType();
      }
      // Effectively this should trigger a runtime exception.
      return super.getJavaSourceType();
    }

    @Override
    public String toString() {
      return String.format(
          this.baseType().getClaroCanonicalTypeNameFmtStr(),
          concreteTypeMappingsForBetterErrorMessages.map(
                  mapping -> {
                    Type mappedType = mapping.getOrDefault(this, this);
                    if (mappedType.equals(this)) {
                      return this.getTypeParamName();
                    }
                    return mappedType.toString();
                  })
              .orElse(this.getTypeParamName())
      );
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder().setGenericTypeParam(
              TypeProtos.GenericTypeParam.newBuilder().setName(this.getTypeParamName()))
          .build();
    }
  }

  // This is really a meta-type which exists primarily to allow some mechanism of holding the name of a generic
  // type param in the scopedheap. This should see very limited use throughout the compiler internals only and
  // none at all by Claro programs.
  @AutoValue
  public abstract static class $Contract extends Type {
    public abstract String getContractName();

    // This disambiguator is necessary in order for two same-named contracts defined in separate Modules to have types
    // correctly considered to be distinct.
    public abstract String getDefiningModuleDisambiguator();

    public abstract ImmutableList<String> getTypeParamNames();

    public abstract ImmutableList<String> getProcedureNames();

    public static $Contract forContractNameTypeParamNamesAndProcedureNames(
        String name, String definingModuleDisambiguator, ImmutableList<String> typeParamNames, ImmutableList<String> procedureNames) {
      return new AutoValue_Types_$Contract(BaseType.$CONTRACT, ImmutableMap.of(), name, definingModuleDisambiguator, typeParamNames, procedureNames);
    }

    @Override
    public String getJavaSourceClaroType() {
      throw new ClaroParserException("Internal Compiler Error: This type should be unreachable in Claro programs.");
    }

    @Override
    public String toString() {
      return String.format(
          baseType().getClaroCanonicalTypeNameFmtStr(),
          getContractName(),
          String.join(", ", getTypeParamNames())
      );
    }

    @Override
    public TypeProto toProto() {
      throw new RuntimeException("Internal Compiler Error: This type <" + this +
                                 "> should not be serialized as it should be an internal only type.");
    }
  }

  @AutoValue
  public abstract static class $ContractImplementation extends Type {
    public abstract String getContractName();

    // This disambiguator is necessary in order to tract where this contract implementation lives so that codegen can
    // target it in the correct namespace. Optional, b/c it's possible the impl lives in the top-level claro_binary().
    public abstract Optional<String> getOptionalDefiningModuleDisambiguator();

    public abstract ImmutableList<Type> getConcreteTypeParams();

    public static $ContractImplementation forContractNameAndConcreteTypeParams(
        String name, Optional<String> optionalDefiningModuleDisambiguator, ImmutableList<Type> concreteTypeParams) {
      return new AutoValue_Types_$ContractImplementation(
          BaseType.$CONTRACT_IMPLEMENTATION, ImmutableMap.of(), name, optionalDefiningModuleDisambiguator, concreteTypeParams);
    }

    @Override
    public String getJavaSourceClaroType() {
      throw new ClaroParserException("Internal Compiler Error: This type should be unreachable in Claro programs.");
    }

    @Override
    public TypeProto toProto() {
      throw new RuntimeException("Internal Compiler Error: This type <" + this +
                                 "> should not be serialized as it should be an internal only type.");
    }
  }

  // This type is interesting in the sense that there's actually no way to manually initialize an instance of this type
  // yourself. The language models this internally, and it is used solely in conjunction with generating an HttpClient.
  @AutoValue
  public abstract static class HttpServiceType extends Type {
    public abstract String getServiceName();

    // This disambiguator is necessary in order for two same-named services defined in separate Modules to have types
    // correctly considered to be distinct.
    public abstract String getDefiningModuleDisambiguator();

    public static HttpServiceType forServiceNameAndDisambiguator(String name, String definingModuleDisambiguator) {
      return new AutoValue_Types_HttpServiceType(BaseType.HTTP_SERVICE, ImmutableMap.of(), name, definingModuleDisambiguator);
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.HttpServiceType.forServiceNameAndDisambiguator(\"%s\", \"%s\")",
          this.getServiceName(),
          this.getDefiningModuleDisambiguator()
      );
    }

    @Override
    public String toString() {
      return String.format(baseType().getClaroCanonicalTypeNameFmtStr(), getServiceName());
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setHttpService(
              TypeProtos.HttpServiceType.newBuilder()
                  .setServiceName(this.getServiceName())
                  .setDefiningModuleDisambiguator(this.getDefiningModuleDisambiguator()))
          .build();
    }
  }

  // In some ways this type is blessed abilities that no other type in the language has. In particular, the compiler
  // will validate that its parameterized type is in fact an HttpService. Other types can only simulate this behavior
  // via initializers.
  @AutoValue
  public abstract static class HttpClientType extends Type {
    public abstract String getServiceName();

    public static HttpClientType forServiceName(String serviceName) {
      return new AutoValue_Types_HttpClientType(BaseType.HTTP_CLIENT, ImmutableMap.of(), serviceName);
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.HttpClientType.forServiceName(\"%s\")",
          getServiceName()
      );
    }

    @Override
    public String getJavaSourceType() {
      // This is super strange, relative to the implementation of all the other types, but what's happening here is that
      // retrofit2 is generating its own implementation of the service definition interface for us to use as the client.
      return getServiceName();
    }

    @Override
    public String toString() {
      return String.format(this.baseType().getClaroCanonicalTypeNameFmtStr(), this.getServiceName());
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setHttpClient(TypeProtos.HttpClientType.newBuilder().setServiceName(this.getServiceName()))
          .build();
    }
  }

  @AutoValue
  public abstract static class HttpServerType extends Type {
    public static final String HTTP_SERVICE_TYPE = "$httpServiceType";

    public static HttpServerType forHttpService(Type httpService) {
      return new AutoValue_Types_HttpServerType(BaseType.HTTP_SERVER, ImmutableMap.of(HTTP_SERVICE_TYPE, httpService));
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.HttpServerType.forHttpService(%s)",
          this.parameterizedTypeArgs().get(HTTP_SERVICE_TYPE).getJavaSourceClaroType()
      );
    }

    @Override
    public String getJavaSourceType() {
      return this.baseType().getJavaSourceFmtStr();
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setHttpServer(
              TypeProtos.HttpServerType.newBuilder()
                  .setService(this.parameterizedTypeArgs().get(HTTP_SERVICE_TYPE).toProto().getHttpService()))
          .build();
    }
  }

  @AutoValue
  public abstract static class $SyntheticOpaqueTypeWrappedValueType
      extends Type implements SupportsMutableVariant<$SyntheticOpaqueTypeWrappedValueType> {

    public abstract boolean getIsMutable();

    // This should *not* be used to expose this implementation detail to consumers of this type as that would validate
    // the semantic constraints intended by a module's choice to export the type as an opaque type.
    public abstract Type getActualWrappedTypeForCodegenPurposesOnly();

    public static $SyntheticOpaqueTypeWrappedValueType create(boolean isMutable, Type actualWrappedType) {
      // This is super trivial, but I may as well cache these. Look at me suddenly pretending like this compiler cares
      // about performance...
      return new AutoValue_Types_$SyntheticOpaqueTypeWrappedValueType(
          BaseType.$SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE,
          ImmutableMap.of(),
          isMutable,
          actualWrappedType
      );
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public String toString() {
      return "...";
    }

    // This synthetic type should never show up at runtime.
    @Override
    public String getJavaSourceClaroType() {
      return this.getActualWrappedTypeForCodegenPurposesOnly().getJavaSourceClaroType();
    }

    @Override
    public String getJavaSourceType() {
      return this.getActualWrappedTypeForCodegenPurposesOnly().getJavaSourceType();
    }

    @Override
    public $SyntheticOpaqueTypeWrappedValueType toShallowlyMutableVariant() {
      throw new RuntimeException("Internal Compiler Error! $SyntheticOpaqueTypeWrappedValueType::toShallowlyMutableVariant is not supported.");
    }

    @Override
    public Optional<$SyntheticOpaqueTypeWrappedValueType> toDeeplyImmutableVariant() {
      return this.getIsMutable() ? Optional.empty() : Optional.of(this);
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder().setOpaqueWrappedValueType(
              TypeProtos.SyntheticOpaqueTypeWrappedValueType.newBuilder().setIsMutable(this.getIsMutable()))
          .build();
    }
  }

  @AutoValue
  public abstract static class $JavaType extends Type implements SupportsMutableVariant<$JavaType> {
    public abstract boolean getIsMutable();

    // E.g. For type defined:
    //     newtype MapFn<In, Out> : $java_type<In, Out>("java.util.function.Function<%s, %s>")
    // and the following concrete parameterization:
    //     MapFn<[int], int>
    // the following codegen will be emitted:
    //     java.util.function.Function<ClaroList<Integer>, Integer>
    public abstract String getFullyQualifiedJavaTypeFmtStr();

    public static $JavaType create(boolean isMutable, ImmutableList<Type> parameterizedTypes, String fullyQualifiedJavaTypeFmtStr) {
      return new AutoValue_Types_$JavaType(
          BaseType.$JAVA_TYPE,
          IntStream.range(0, parameterizedTypes.size()).boxed()
              .collect(ImmutableMap.toImmutableMap(
                  // This is limiting the usage of these types to parameterizing over generic types.
                  Object::toString,
                  parameterizedTypes::get
              )),
          isMutable,
          fullyQualifiedJavaTypeFmtStr
      );
    }

    @Override
    public String toString() {
      return "...";
    }

    @Override
    public String getJavaSourceType() {
      return String.format(
          this.getFullyQualifiedJavaTypeFmtStr(),
          this.parameterizedTypeArgs().values().stream().map(Type::getJavaSourceType).toArray()
      );
    }

    @Override
    public String getJavaSourceClaroType() {
      return String.format(
          "Types.$JavaType.create(%s, ImmutableList.of(%s), \"%s\")",
          this.getIsMutable(),
          this.parameterizedTypeArgs().values().stream()
              .map(Type::getJavaSourceClaroType)
              .collect(Collectors.joining(", ")),
          this.getFullyQualifiedJavaTypeFmtStr()
      );
    }

    @Override
    public TypeProto toProto() {
      return TypeProto.newBuilder()
          .setSyntheticJavaType(
              TypeProtos.SyntheticJavaType.newBuilder()
                  .setIsMutable(this.getIsMutable())
                  .addAllParameterizedTypes(
                      this.parameterizedTypeArgs().values().stream().map(Type::toProto).collect(Collectors.toList()))
                  .setFullyQualifiedJavaTypeFmtStr(this.getFullyQualifiedJavaTypeFmtStr())
          ).build();
    }

    @Override
    public boolean isMutable() {
      return this.getIsMutable();
    }

    @Override
    public $JavaType toShallowlyMutableVariant() {
      return $JavaType.create(
          /*isMutable=*/true, this.parameterizedTypeArgs().values().asList(), this.getFullyQualifiedJavaTypeFmtStr());
    }

    @Override
    public Optional<$JavaType> toDeeplyImmutableVariant() {
      if (!this.getIsMutable()) {
        return Optional.of(this);
      }
      // There's no way to coerce a $JavaType to be immutable if it isn't already.
      return Optional.empty();
    }
  }


  // This is gonna be used to convey to AutoValue that certain values are nullable and it will generate null-friendly
  // constructors and .equals() and .hashCode() methods.
  // https://github.com/google/auto/blob/master/value/userguide/howto.md#nullable
  @interface Nullable {
  }

  public static boolean isDeeplyImmutable(Type type) {
    if (type instanceof SupportsMutableVariant<?>) {
      // Quickly, if this outer layer is mutable, then we already know the overall type is not *deeply* immutable.
      if (((SupportsMutableVariant<?>) type).isMutable()) {
        return false;
      }
      // So now, whether the type is deeply-immutable or not strictly depends on the parameterized types.
      switch (type.baseType()) {
        case LIST:
          return isDeeplyImmutable(((ListType) type).getElementType());
        case SET:
          return isDeeplyImmutable(type.parameterizedTypeArgs().get(SetType.PARAMETERIZED_TYPE));
        case MAP:
          return isDeeplyImmutable(type.parameterizedTypeArgs().get(MapType.PARAMETERIZED_TYPE_KEYS))
                 && isDeeplyImmutable(type.parameterizedTypeArgs().get(MapType.PARAMETERIZED_TYPE_VALUES));
        case TUPLE:
          return type.parameterizedTypeArgs().values().stream()
              .allMatch(Types::isDeeplyImmutable);
        case STRUCT:
          return ((StructType) type).getFieldTypes().stream()
              .allMatch(Types::isDeeplyImmutable);
        case $SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE:
          return true; // Would've already returned false above if it was mutable.
        case $JAVA_TYPE:
          return true; // Would've already returned false above if it was mutable.
        default:
          throw new RuntimeException("Internal Compiler Error: Unsupported structured type found in isDeeplyImmutable()!");
      }
    } else if (type.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      // User defined types are inherently shallow-ly immutable, so whether they're deeply-immutable simply depends on
      // recursing into the wrapped type. If the wrapped type is deeply-immutable, then it also depends on the
      // mutability of any parameterized types.
      return isDeeplyImmutable(
          UserDefinedType.$resolvedWrappedTypes.get(
              String.format(
                  "%s$%s",
                  ((UserDefinedType) type).getTypeName(),
                  ((UserDefinedType) type).getDefiningModuleDisambiguator()
              )))
             && type.parameterizedTypeArgs().values().stream().allMatch(Types::isDeeplyImmutable);
    } else if (type.baseType().equals(BaseType.FUTURE)) {
      // Futures are inherently shallow-ly immutable, so whether they're deeply-immutable simply depends on recursing
      // into the wrapped type.
      return isDeeplyImmutable(type.parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY));
    } else {
      return true;
    }
  }

  public static Optional<? extends Type> getDeeplyImmutableVariantTypeRecommendationForError(Type type) {
    switch (type.baseType()) {
      case LIST:
      case SET:
      case MAP:
      case TUPLE:
      case STRUCT:
        return ((SupportsMutableVariant<?>) type).toDeeplyImmutableVariant();
      case USER_DEFINED_TYPE:
        return ((UserDefinedType) type).toDeeplyImmutableVariant();
      case FUTURE:
        // Future can't itself support a toDeeplyImmutableVariant() method because there's actually no such conversion
        // that's *actually* valid in Claro semantics. Here we're doing this *only in the context of providing a nice
        // recommendation to the user once an error has already been identified*.
        Optional<? extends Type> optionalWrappedDeeplyImmutableVariantType =
            getDeeplyImmutableVariantTypeRecommendationForError(
                type.parameterizedTypeArgs().get(FutureType.PARAMETERIZED_TYPE_KEY));
        if (!optionalWrappedDeeplyImmutableVariantType.isPresent()) {
          return Optional.empty();
        }
        return Optional.of(FutureType.wrapping(optionalWrappedDeeplyImmutableVariantType.get()));
      default: // Everything else should already be inherently immutable.
        return Optional.of(type);
    }
  }

  public static Type parseTypeProto(TypeProto typeProto) {
    switch (typeProto.getTypeCase()) {
      case PRIMITIVE:
        switch (typeProto.getPrimitive().name()) {
          case "UNKNOWN_PRIMITIVE_TYPE":
            throw new RuntimeException("Internal Compiler Error: Parsed TypeProto containing unknown primitive type: " +
                                       typeProto.getPrimitive().getValueDescriptor());
          case "INTEGER":
            return INTEGER;
          case "FLOAT":
            return FLOAT;
          case "STRING":
            return STRING;
          case "BOOLEAN":
            return BOOLEAN;
          case "HTTP_RESPONSE":
            return HTTP_RESPONSE;
        }
      case ATOM:
        TypeProtos.AtomType atomTypeProto = typeProto.getAtom();
        return AtomType.forNameAndDisambiguator(atomTypeProto.getName(), atomTypeProto.getDefiningModuleDisambiguator());
      case LIST:
        TypeProtos.ListType listTypeProto = typeProto.getList();
        return ListType.forValueType(parseTypeProto(listTypeProto.getElementType()), listTypeProto.getIsMutable());
      case MAP:
        TypeProtos.MapType mapTypeProto = typeProto.getMap();
        return MapType.forKeyValueTypes(
            parseTypeProto(mapTypeProto.getKeyType()),
            parseTypeProto(mapTypeProto.getValueType()),
            mapTypeProto.getIsMutable()
        );
      case SET:
        TypeProtos.SetType setTypeProto = typeProto.getSet();
        return SetType.forValueType(parseTypeProto(setTypeProto.getElementType()), setTypeProto.getIsMutable());
      case TUPLE:
        TypeProtos.TupleType tupleTypeProto = typeProto.getTuple();
        return TupleType.forValueTypes(
            tupleTypeProto.getElementTypesList().stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            tupleTypeProto.getIsMutable()
        );
      case ONEOF:
        return OneofType.forVariantTypes(
            typeProto.getOneof().getVariantTypesList().stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()));
      case STRUCT:
        TypeProtos.StructType structTypeProto = typeProto.getStruct();
        return StructType.forFieldTypes(
            ImmutableList.copyOf(structTypeProto.getFieldNamesList()),
            structTypeProto.getFieldTypesList()
                .stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            structTypeProto.getIsMutable()
        );
      case FUTURE:
        return FutureType.wrapping(parseTypeProto(typeProto.getFuture().getWrappedType()));
      case FUNCTION:
        TypeProtos.FunctionType functionTypeProto = typeProto.getFunction();
        return ProcedureType.FunctionType.forArgsAndReturnTypes(
            functionTypeProto.getArgTypesList()
                .stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            parseTypeProto(functionTypeProto.getOutputType()),
            BaseType.FUNCTION,
            // TODO(steving) DROP SUPPORT FOR INJECTED KEYS NOW THAT THE MODULE SYSTEM IS IN PLACE TO SUPERSEDE IT.
            /*directUsedInjectedKeys=*/ImmutableSet.of(),
            /*procedureDefinitionStmt=*/null,
            ProcedureType.getBlockingAnnotationFromProto(functionTypeProto.getAnnotatedBlocking()),
            functionTypeProto.getOptionalAnnotatedBlockingGenericOverArgsCount() == 0
            ? Optional.empty()
            : Optional.of(ImmutableSet.copyOf(functionTypeProto.getOptionalAnnotatedBlockingGenericOverArgsList())),
            functionTypeProto.getOptionalGenericTypeParamNamesCount() == 0
            ? Optional.empty()
            : Optional.of(ImmutableList.copyOf(functionTypeProto.getOptionalGenericTypeParamNamesList())),
            Optional.of(
                functionTypeProto.getRequiredContractsList().stream()
                    .collect(ImmutableListMultimap.toImmutableListMultimap(
                        r -> String.format("%s$%s", r.getName(), r.getDefiningModuleDisambiguator()),
                        r -> r.getGenericTypeParamsList().stream()
                            .map($GenericTypeParam::forTypeParamName)
                            .collect(ImmutableList.toImmutableList())
                    )))
        );
      case CONSUMER:
        TypeProtos.ConsumerType consumerTypeProto = typeProto.getConsumer();
        return ProcedureType.ConsumerType.forConsumerArgTypes(
            consumerTypeProto.getArgTypesList()
                .stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            BaseType.CONSUMER_FUNCTION,
            // TODO(steving) DROP SUPPORT FOR INJECTED KEYS NOW THAT THE MODULE SYSTEM IS IN PLACE TO SUPERSEDE IT.
            /*directUsedInjectedKeys=*/ImmutableSet.of(),
            /*procedureDefinitionStmt=*/null,
            ProcedureType.getBlockingAnnotationFromProto(consumerTypeProto.getAnnotatedBlocking()),
            consumerTypeProto.getOptionalAnnotatedBlockingGenericOverArgsCount() == 0
            ? Optional.empty()
            : Optional.of(ImmutableSet.copyOf(consumerTypeProto.getOptionalAnnotatedBlockingGenericOverArgsList())),
            consumerTypeProto.getOptionalGenericTypeParamNamesCount() == 0
            ? Optional.empty()
            : Optional.of(ImmutableList.copyOf(consumerTypeProto.getOptionalGenericTypeParamNamesList())),
            Optional.of(
                consumerTypeProto.getRequiredContractsList().stream()
                    .collect(ImmutableListMultimap.toImmutableListMultimap(
                        r -> String.format("%s$%s", r.getName(), r.getDefiningModuleDisambiguator()),
                        r -> r.getGenericTypeParamsList().stream()
                            .map($GenericTypeParam::forTypeParamName)
                            .collect(ImmutableList.toImmutableList())
                    )))
        );
      case PROVIDER:
        TypeProtos.ProviderType providerTypeProto = typeProto.getProvider();
        return ProcedureType.ProviderType.forReturnType(
            parseTypeProto(providerTypeProto.getOutputType()),
            /*overrideBaseType=*/BaseType.PROVIDER_FUNCTION,
            // TODO(steving) DROP SUPPORT FOR INJECTED KEYS NOW THAT THE MODULE SYSTEM IS IN PLACE TO SUPERSEDE IT.
            /*directUsedInjectedKeys=*/ImmutableSet.of(),
            /*procedureDefinitionStmt=*/null,
            ProcedureType.getBlockingAnnotationFromProto(providerTypeProto.getAnnotatedBlocking()),
            providerTypeProto.getOptionalGenericTypeParamNamesCount() > 0
            ? Optional.of(ImmutableList.copyOf(providerTypeProto.getOptionalGenericTypeParamNamesList()))
            : Optional.empty(),
            Optional.of(
                providerTypeProto.getRequiredContractsList().stream()
                    .collect(ImmutableListMultimap.toImmutableListMultimap(
                        r -> String.format("%s$%s", r.getName(), r.getDefiningModuleDisambiguator()),
                        r -> r.getGenericTypeParamsList().stream()
                            .map($GenericTypeParam::forTypeParamName)
                            .collect(ImmutableList.toImmutableList())
                    )))
        );
      case USER_DEFINED_TYPE:
        TypeProtos.UserDefinedType userDefinedTypeProto = typeProto.getUserDefinedType();
        return UserDefinedType.forTypeNameAndParameterizedTypes(
            userDefinedTypeProto.getTypeName(),
            userDefinedTypeProto.getDefiningModuleDisambiguator(),
            userDefinedTypeProto.getParameterizedTypesList()
                .stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList())
        );
      case GENERIC_TYPE_PARAM:
        return $GenericTypeParam.forTypeParamName(typeProto.getGenericTypeParam().getName());
      case HTTP_SERVICE:
        TypeProtos.HttpServiceType httpServiceTypeProto = typeProto.getHttpService();
        return HttpServiceType.forServiceNameAndDisambiguator(
            httpServiceTypeProto.getServiceName(),
            httpServiceTypeProto.getDefiningModuleDisambiguator()
        );
      case HTTP_CLIENT:
        return HttpClientType.forServiceName(typeProto.getHttpClient().getServiceName());
      case HTTP_SERVER:
        httpServiceTypeProto = typeProto.getHttpServer().getService();
        return HttpServerType.forHttpService(
            HttpServiceType.forServiceNameAndDisambiguator(
                httpServiceTypeProto.getServiceName(),
                httpServiceTypeProto.getDefiningModuleDisambiguator()
            ));
      case OPAQUE_WRAPPED_VALUE_TYPE:
        TypeProtos.SyntheticOpaqueTypeWrappedValueType opaqueTypeProto = typeProto.getOpaqueWrappedValueType();
        return $SyntheticOpaqueTypeWrappedValueType.create(
            opaqueTypeProto.getIsMutable(),
            parseTypeProto(typeProto.getOpaqueWrappedValueType().getActualWrappedType())
        );
      case SYNTHETIC_JAVA_TYPE:
        TypeProtos.SyntheticJavaType javaTypeProto = typeProto.getSyntheticJavaType();
        return $JavaType.create(
            javaTypeProto.getIsMutable(),
            javaTypeProto.getParameterizedTypesList().stream()
                .map(Types::parseTypeProto)
                .collect(ImmutableList.toImmutableList()),
            javaTypeProto.getFullyQualifiedJavaTypeFmtStr()
        );
      default:
        throw new RuntimeException("Internal Compiler Error: This unknown proto type <" + typeProto.getTypeCase() +
                                   "> could not be deserialized.");
    }
  }
}
