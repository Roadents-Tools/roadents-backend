package com.reroute.backend.utils;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Contains a variety of useful methods and objects for dealing with streams.
 */
public final class StreamUtils {

    private StreamUtils() {
    }

    /**
     * Collects a stream into a map with specific keys. If 2 items have the same keys, the final is kept.
     *
     * @param applicationFunction the function to use to generate the new keys
     * @return a collector that collects a Stream of type T into a map with keys type T and values type R
     */
    public static <T, R> Collector<T, ?, Map<R, T>> collectWithKeys(Function<T, R> applicationFunction) {
        return collectWithMapping(applicationFunction, Function.identity());
    }

    /**
     * Collects a string into a map with the specified mappings. If 2 items have the same keys, the final is kept.
     * @param keyMapping the function to use on each item to get the key for the map
     * @param valueMapping the function to use on each item to get the value for the map
     * @return a collector that collects a Stream of type I into a map with keys type K and values type V
     */
    public static <I, K, V> Collector<I, ?, Map<K, V>> collectWithMapping(Function<I, K> keyMapping, Function<I, V> valueMapping) {
        return new Collector<I, Map<K, V>, Map<K, V>>() {
            @Override
            public Supplier<Map<K, V>> supplier() {
                return ConcurrentHashMap::new;
            }

            @Override
            public BiConsumer<Map<K, V>, I> accumulator() {
                return (map, ival) -> {
                    V value = valueMapping.apply(ival);
                    K key = keyMapping.apply(ival);
                    if (key != null && value != null) map.put(key, value);
                };
            }

            @Override
            public BinaryOperator<Map<K, V>> combiner() {
                return (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                };
            }

            @Override
            public Function<Map<K, V>, Map<K, V>> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet(
                        Characteristics.UNORDERED,
                        Characteristics.IDENTITY_FINISH,
                        Characteristics.CONCURRENT
                );
            }
        };
    }

    /**
     * Collects a stream into a map with specific values. If 2 items have the same keys, the final is kept.
     * @param applicationFunction the function to use to generate the new values
     * @return a collector that collects a Stream of type T into a map with keys type T and values type R
     */
    public static <T, R> Collector<T, ?, Map<T, R>> collectWithValues(Function<T, R> applicationFunction) {
        return collectWithMapping(Function.identity(), applicationFunction);
    }

    /**
     * Collects a stream into a string separated by a spacer string.
     * @param seperator the string to insert between objects
     * @return a string formed by calling each element's toString() method and appending the spacer
     */
    public static Collector<Object, ?, String> joinToString(String seperator) {
        return new Collector<Object, StringJoiner, String>() {
            @Override
            public Supplier<StringJoiner> supplier() {
                return () -> new StringJoiner(seperator);
            }

            @Override
            public BiConsumer<StringJoiner, Object> accumulator() {
                return (stringJoiner, o) -> stringJoiner.add(o.toString());
            }

            @Override
            public BinaryOperator<StringJoiner> combiner() {
                return StringJoiner::merge;
            }

            @Override
            public Function<StringJoiner, String> finisher() {
                return StringJoiner::toString;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet();
            }
        };
    }

    /**
     * Streams a boolean array.
     * @param arr the array to stream
     * @return a stream of the array
     */
    public static Stream<Boolean> streamBoolArray(boolean[] arr) {
        List<Boolean> buf = new ArrayList<>(arr.length);
        for (boolean b : arr) {
            buf.add(b);
        }
        return buf.stream();
    }
}
