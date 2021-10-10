package com.claro.intermediate_representation.types.impls.user_defined_impls.structs;

import com.claro.ClaroParserException;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.user_defined_impls.ClaroUserDefinedTypeImplementation;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ClaroStruct<T extends ClaroStruct<T>> extends ClaroUserDefinedTypeImplementation {
  protected final Types.StructType structType;
  protected final Map<String, Object> delegateFieldMap;

  public ClaroStruct(Types.StructType structType, Map<String, Object> delegateFieldMap) {
    this.structType = structType;
    this.delegateFieldMap = delegateFieldMap;
  }

  public final Object get(String identifier) throws ClaroTypeException {
    if (delegateFieldMap.containsKey(identifier)) {
      return delegateFieldMap.get(identifier);
    }
    throw ClaroTypeException.forInvalidMemberReference(this.structType, identifier);
  }

  public abstract void set(String identifier, Object value) throws ClaroTypeException;

  @Override
  public String toString() {
    return delegateFieldMap.entrySet().stream()
        .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(", ", this.structType.getName() + "{", "}"));
  }

  @Override
  public Type getClaroType() {
    return this.structType;
  }

  public static <T extends ClaroStruct<T>> Builder<T> builderForType(Types.StructType structType) {
    return new Builder<>(structType);
  }

  // TODO(steving) Add a toString for this builder. But also add a Type for the type system.
  public static class Builder<T extends ClaroUserDefinedTypeImplementation>
      implements ClaroUserDefinedTypeImplementationBuilder<T> {

    private final Types.StructType structType;
    private final HashMap<String, Object> builderFieldMap;

    public Builder(Types.StructType structType) {
      this.structType = structType;
      this.builderFieldMap = new HashMap<>();
    }

    @Override
    public Type getClaroType() {
      return Types.BuilderType.forStructType(this.structType);
    }

    /**
     * Set the type of the field with the given name. This method completely assumes that type validation has already
     * been previously performed before calling this method.
     *
     * @param fieldName name of the field whose value you're setting.
     * @param value     the value set for the given field.
     * @return this instance for chaining.
     * @throws ClaroTypeException if the given field name is not valid.
     */
    public Builder<T> setField(String fieldName, Object value) throws ClaroTypeException {
      if (this.structType.getFieldTypes().containsKey(fieldName)) {
        builderFieldMap.put(fieldName, value);
      }
      throw ClaroTypeException.forInvalidMemberReference(this.structType, fieldName);
    }

    // TODO(steving) Add an optional type, and make the builder automatigically handle optional types by defaulting to
    //  Optional::empty when there's no value set.

    /**
     * Validates that all fields have been set (because Claro rejects the notion of null values) and then returns a new
     * instance of the Struct being built.
     *
     * @return the new instance of the Struct being built.
     * @throws ClaroTypeException if every field has not been set.
     */
    @SuppressWarnings("unchecked")
    @Override
    public T build() throws ClaroTypeException {
      ImmutableSet<Map.Entry<String, Type>> unsetFields =
          Sets.difference(this.structType.getFieldTypes().entrySet(), this.builderFieldMap.entrySet()).immutableCopy();
      if (unsetFields.isEmpty()) {
        try {
          // Come on Jason....there's gotta be a better way to do this....although if this is the best way to do this in
          // Java's version of inheritance, then I think I'm done with OO and want to throw it away for something new.
          return (T) this.getClass().getSuperclass()
              .getConstructor(Types.StructType.class, Map.class)
              .newInstance(this.structType, this.builderFieldMap);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
          throw new ClaroParserException("");
        }
      }
      throw ClaroTypeException.forUnsetRequiredStructMember(this.structType, unsetFields);
    }

    @Override
    public String toString() {
      return String.format(
          "builder<%s>{%s}",
          this.structType.getName(),
          this.builderFieldMap.entrySet().stream()
              .map(entry -> String.format(
                  "%s: %s", entry.getKey(), entry.getKey() == null ? "unset" : entry.getValue()))
              .collect(Collectors.joining(", "))
      );
    }
  }
}
