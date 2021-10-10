package com.claro.intermediate_representation.types.impls.user_defined_impls.structs;

import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;

import java.util.HashMap;
import java.util.Map;

public final class MutableClaroStruct extends ClaroStruct<MutableClaroStruct> {
  public MutableClaroStruct(Types.StructType structType, Map<String, Object> delegateFieldMap) {
    super(structType, new HashMap<>(delegateFieldMap));
  }

  @Override
  public void set(String identifier, Object value) throws ClaroTypeException {
    if (this.delegateFieldMap.containsKey(identifier)) {
      this.delegateFieldMap.replace(identifier, value);
    } else {
      throw ClaroTypeException.forInvalidMemberReference(this.structType, identifier);
    }
  }

  // TODO(steving) This entire thought process below is only true for the type itself, not for the actual
  //  runtime implementation object. Because the object at runtime, specifically for a STRUCT (different
  //  for a Class) has NO mechanism for an inner defined struct to access members in the outer structs
  //  surrounding it. This is a key distinction between a Struct and a Class. A struct simply is a container
  //  type, it doesn't encapsulate behavior.
  // Structs should allow nested struct definitions. This means that a struct needs to have access to
  // struct definitions that are defined somewhere within the context of the tree of scopes implicitly
  // defined by the top-level struct itself.

  // Every mutable struct needs a Scope for itself
  // AND
  //    (a second Scope pointing out to the Scope it's contained in, in the case that it was
  //     declared as a nested Struct.
  // OR
  //     a ScopedHeap pointing out to the Scope that it's contained in, in the case that it was
  //     declared in a stmt-list context.)

  // We need a method for getting access to things in the nested scopes visible to these structs.
  // Object getTypeDefinition(String typeName) {
  //   if (this.scope.scopedSymbolTable.contains(typeName)) {
  //     return ...
  //   } else if (this.parentStruct.isPresent()) {
  //     return this.parentStruct.getTypeDefinition(typeName);
  //   } else {
  //     return this.surroundingScopedHeap.getIdentifierValue(typeName);
  //   }
  // }
}
