package com.claro.intermediate_representation.types.impls.builtins_impls.collections;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClaroMap<K, V> extends HashMap<K, V> implements ClaroBuiltinTypeImplementation, Iterable<ClaroTuple> {
  private final Types.MapType claroType;

  public ClaroMap(Types.MapType claroType) {
    super();
    this.claroType = claroType;
  }

  public ClaroMap(Types.MapType claroType, Map<K, V> from) {
    super(from);
    this.claroType = claroType;
  }

  public ClaroMap(Types.MapType claroType, int initialSize) {
    super(initialSize);
    this.claroType = claroType;
  }

  public V getElement(K k) {
    return super.get(k);
  }

  public ClaroMap<K, V> set(K k, V v) {
    super.put(k, v);
    return this;
  }

  public int length() {
    return super.size();
  }

  @Override
  public Type getClaroType() {
    return this.claroType;
  }

  @Override
  public String toString() {
    return this.entrySet().stream()
        .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(", ", this.claroType.isMutable() ? "mut {" : "{", "}"));
  }

  @Override
  public Iterator<ClaroTuple> iterator() {
    return new $ClaroMapIterator(
        this,
        Types.TupleType.forValueTypes(
            ImmutableList.of(
                this.claroType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS),
                this.claroType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
            ),
            /*isMutable=*/false
        )
    );
  }

  public Stream<ClaroTuple> stream() {
    return Streams.stream(this.iterator());
  }

  private class $ClaroMapIterator implements Iterator<ClaroTuple> {
    private final Iterator<Entry<K, V>> entriesIterator;
    private final Types.TupleType itemType;

    $ClaroMapIterator(ClaroMap<K, V> map, Types.TupleType itemType) {

      this.entriesIterator = map.entrySet().iterator();
      this.itemType = itemType;
    }

    @Override
    public boolean hasNext() {
      return this.entriesIterator.hasNext();
    }

    @Override
    public ClaroTuple next() {
      Entry<K, V> nextEntry = entriesIterator.next();
      return new ClaroTuple(itemType, nextEntry.getKey(), nextEntry.getValue());
    }
  }
}