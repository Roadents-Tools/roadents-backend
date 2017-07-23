package org.tymit.projectdonut.logic.utils;

import com.google.common.collect.Sets;
import org.tymit.projectdonut.model.location.DestinationLocation;
import org.tymit.projectdonut.model.routing.TravelRoute;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class StreamUtils {
    public static final Collector<? super TravelRoute, ?, Map<DestinationLocation, TravelRoute>> OPTIMAL_ROUTES_FOR_DESTINATIONS = new Collector<TravelRoute, Map<DestinationLocation, TravelRoute>, Map<DestinationLocation, TravelRoute>>() {

        @Override
        public Supplier<Map<DestinationLocation, TravelRoute>> supplier() {
            return ConcurrentHashMap::new;
        }

        @Override
        public BiConsumer<Map<DestinationLocation, TravelRoute>, TravelRoute> accumulator() {
            return (curmap, route) -> {
                DestinationLocation dest = route.getDestination();
                TravelRoute current = curmap.get(dest);
                if (current == null || current.getTotalTime().getDeltaLong() > route.getTotalTime().getDeltaLong())
                    curmap.put(dest, route);
            };
        }

        @Override
        public BinaryOperator<Map<DestinationLocation, TravelRoute>> combiner() {
            return (curmap, curmap2) -> {
                for (DestinationLocation key : curmap2.keySet()) {
                    TravelRoute current = curmap.get(key);
                    TravelRoute current2 = curmap2.get(key);
                    if (current == null || current.getTotalTime().getDeltaLong() > current2.getTotalTime()
                            .getDeltaLong())
                        curmap.put(key, current2);
                }
                return curmap;
            };
        }

        @Override
        public Function<Map<DestinationLocation, TravelRoute>, Map<DestinationLocation, TravelRoute>> finisher() {
            return curmap -> curmap;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.newHashSet(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
        }
    };

    private StreamUtils() {
    }

    public static <T, R> Collector<T, ?, Map<R, T>> collectWithKeys(Function<T, R> applicationFunction) {
        return new Collector<T, Map<R, T>, Map<R, T>>() {

            @Override
            public Supplier<Map<R, T>> supplier() {
                return ConcurrentHashMap::new;
            }

            @Override
            public BiConsumer<Map<R, T>, T> accumulator() {
                return (map, tval) -> map.put(applicationFunction.apply(tval), tval);
            }

            @Override
            public BinaryOperator<Map<R, T>> combiner() {
                return (rtMap, rtMap2) -> {
                    rtMap.putAll(rtMap2);
                    return rtMap;
                };
            }

            @Override
            public Function<Map<R, T>, Map<R, T>> finisher() {
                return a -> a;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
            }
        };
    }

    public static <T, R> Collector<T, ?, Map<T, R>> collectWithValues(Function<T, R> applicationFunction) {
        return new Collector<T, Map<T, R>, Map<T, R>>() {

            @Override
            public Supplier<Map<T, R>> supplier() {
                return ConcurrentHashMap::new;
            }

            @Override
            public BiConsumer<Map<T, R>, T> accumulator() {
                return (map, tval) -> map.put(tval, applicationFunction.apply(tval));
            }

            @Override
            public BinaryOperator<Map<T, R>> combiner() {
                return (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                };
            }

            @Override
            public Function<Map<T, R>, Map<T, R>> finisher() {
                return a -> a;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
            }
        };
    }
}
