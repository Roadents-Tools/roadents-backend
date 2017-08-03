package org.tymit.projectdonut.utils;

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

public final class StreamUtils {

    private StreamUtils() {
    }

    public static <T, R> Collector<T, ?, Map<R, T>> collectWithKeys(Function<T, R> applicationFunction) {
        return collectWithMapping(applicationFunction, a -> a);
    }

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
                    if (value != null) map.put(keyMapping.apply(ival), value);
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
                return a -> a;
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

    public static <T, R> Collector<T, ?, Map<T, R>> collectWithValues(Function<T, R> applicationFunction) {
        return collectWithMapping(a -> a, applicationFunction);
    }

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

    public static Stream<Boolean> streamBoolArray(boolean[] arr) {
        List<Boolean> buf = new ArrayList<>(arr.length);
        for (boolean b : arr) {
            buf.add(b);
        }
        return buf.stream();
    }
}
