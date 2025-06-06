/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.cql3.ResultSet.ResultMetadata;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.ByteBufferAccessor;
import org.apache.cassandra.db.marshal.TupleType;
import org.apache.cassandra.db.marshal.ValueAccessor;

public class ObjectSerializer51x<T> implements ObjectSerializer<T> {
  public final ImmutableSortedMap<String, FieldSerializer> serializers;

  public class FieldSerializer {
    public final AbstractType type;
    public final Function<T, Object> accessor;

    FieldSerializer(AbstractType type, Function<T, Object> accessor) {
      this.type = type;
      this.accessor = accessor;
    }

    FieldSerializer(AbstractType type, final Field field) {
      field.setAccessible(true);
      this.type = type;
      this.accessor =
          (obj) -> {
            try {
              return field.get(obj);
            } catch (IllegalAccessException e) {
              throw new AssertionError("Should not happen as we set the field to accessible.");
            }
          };
    }

    ByteBuffer serializeField(T obj) {
      Object value = accessor.apply(obj);
      if (value == null) {
        return null;
      }
      return type.getSerializer().serialize(accessor.apply(obj));
    }
  }

  /**
   * Due to the magic of java generics, the class doesn't have the full generic information, hence
   * the double types. Also, this will only serialize **PUBLIC** fields (perhaps this should be
   * changed; it's not totally clear). Tag accordingly.
   */
  public ObjectSerializer51x(Class<T> clazz, Type genericType) {
    serializers =
        GenericSerializer51x.simpleType(genericType)
            ? ImmutableSortedMap.<String, FieldSerializer>of(
                "result", new FieldSerializer(GenericSerializer51x.getType(genericType), x -> x))
            : ImmutableSortedMap.copyOf(
                Arrays.stream(clazz.getFields())
                    .collect(
                        Collectors.toMap(
                            field -> field.getName(),
                            field ->
                                new FieldSerializer(
                                    GenericSerializer51x.getType(field.getGenericType()), field))));
    // currently not recursive; multiple ways to do it
  }

  public ObjectSerializer51x(Class<T> clazz) {
    this(clazz, clazz);
  }

  /**
   * Serialize an object into a C* ResultSet, with each field as a named value.
   *
   * @param obj The object to serialize
   * @param ksName Pretend we are coming from this keyspace
   * @param cfName Pretend we are coming from this columnfamily
   */
  public ResultSet toResultSet(T obj, String ksName, String cfName) {
    return new ResultSet(
        new ResultMetadata(
            serializers.entrySet().stream()
                .map(
                    e ->
                        new ColumnSpecification(
                            ksName,
                            cfName,
                            new ColumnIdentifier(e.getKey(), true),
                            e.getValue().type))
                .collect(Collectors.toList())),
        Lists.<List<ByteBuffer>>newArrayList(toByteBufferList(obj)));
  }

  /**
   * Serialize an object into a C* multi-row ResultSet, with each field as a named value.
   *
   * @param obj The object to serialize
   * @param ksName Pretend we are coming from this keyspace
   * @param cfName Pretend we are coming from this columnfamily
   */
  public ResultSet toMultiRowResultSet(Collection<T> obj, String ksName, String cfName) {
    return new ResultSet(
        new ResultMetadata(
            serializers.entrySet().stream()
                .map(
                    e ->
                        new ColumnSpecification(
                            ksName,
                            cfName,
                            new ColumnIdentifier(e.getKey(), true),
                            e.getValue().type))
                .collect(Collectors.toList())),
        obj.stream().map(this::toByteBufferList).collect(Collectors.toList()));
  }

  public List<ByteBuffer> toByteBufferList(T obj) {
    return serializers.values().stream()
        .map(fs -> fs.serializeField(obj))
        .collect(Collectors.toList());
  }

  public ByteBuffer toByteBuffer(T obj) {
    // use reflection to figure out if we have the CEP-21 (accord) signature or the latest in trunk
    try {
      Method pack;
      try {
        pack = TupleType.class.getDeclaredMethod("pack", ValueAccessor.class, Collection.class);
        return (ByteBuffer)
            (pack.invoke(
                null,
                ByteBufferAccessor.instance,
                Arrays.asList(
                    serializers.values().stream()
                        .map(fs -> fs.serializeField(obj))
                        .toArray(ByteBuffer[]::new))));
      } catch (NoSuchMethodException ex) {
        // try the CEP-21 version
        pack = TupleType.class.getDeclaredMethod("pack", ValueAccessor.class, Object[].class);
        return (ByteBuffer)
            (pack.invoke(
                null,
                ByteBufferAccessor.instance,
                serializers.values().stream()
                    .map(fs -> fs.serializeField(obj))
                    .toArray(ByteBuffer[]::new)));
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
