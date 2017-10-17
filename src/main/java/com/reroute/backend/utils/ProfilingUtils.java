package com.reroute.backend.utils;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 8/24/16.
 */
public class ProfilingUtils {

    private static final Map<String, Set<MethodTimer>> times = new ConcurrentHashMap<>();
    private static Thread profilerThread = null;

    public static void printDataEvery(long millis) {
        if (profilerThread != null) {
            profilerThread.interrupt();
            profilerThread = null;
        }
        profilerThread = new Thread(() -> {
            while (profilerThread != null) {
                LoggingUtils.logMessage("Profiler", "Profiling statistics:\n" + printData());
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        profilerThread.start();
    }

    public static String printData() {
        ProfilingUtils.MethodTimer ourtm = ProfilingUtils.startTimer("Profile");
        StringBuilder builder = new StringBuilder();

        List<String> toSort = new ArrayList<>();
        toSort.addAll(getMethodTags());
        toSort.sort(Comparator.comparing(ProfilingUtils::getTotalTime).reversed());
        for (String methodTag : toSort) {
            String format = String.format("Method: %45s    Time: %10d    Percent: %f\n", methodTag, getTotalTime(methodTag), getTimeAsPercent(methodTag));
            builder.append(format);
        }
        long timeTotal = times.values().parallelStream()
                .flatMap(Collection::parallelStream)
                .mapToLong(MethodTimer::getTimeDelta)
                .sum();
        builder.append("Sum Time: ").append(timeTotal).append(" Sum Percent: 100\n");
        long startTime = times.values()
                .stream()
                .flatMap(Collection::stream)
                .mapToLong(tm -> tm.startTime)
                .min()
                .orElse(-1);
        if (startTime > 0) {
            long now = System.currentTimeMillis();
            long diff = now - startTime;
            double percent = timeTotal * 100. / diff;
            builder.append("Total Time: ").append(diff).append(" Total Percent: ").append(percent);
        } else {
            builder.append("Total Time: 0 Total Percent: undefined");
        }



        times.values().stream()
                .flatMap(Collection::stream)
                .filter(timer -> timer.endTime == -1)
                .forEach(timer -> LoggingUtils.logMessage("Profiler", timer.toString() + "\n"));

        ourtm.stop();
        return builder.toString();
    }

    public static Set<String> getMethodTags() {
        return times.keySet();
    }

    public static double getTimeAsPercent(String tag) {
        if (!times.containsKey(tag)) return 0;
        long allTime = times.keySet().stream()
                .flatMap(key -> times.get(key).stream())
                .mapToLong(MethodTimer::getTimeDelta)
                .sum();
        return 100.0 * getTotalTime(tag) / allTime;
    }

    public static long getTotalTime(String tag) {
        if (!times.containsKey(tag)) return 0;
        return times.get(tag).stream()
                .mapToLong(MethodTimer::getTimeDelta)
                .sum();
    }

    public static void stopPrinting() {
        if (profilerThread != null) {
            profilerThread.interrupt();
        }
        profilerThread = null;
    }

    public static MethodTimer startTimer(String tag) {
        return new MethodTimer(tag, System.currentTimeMillis());
    }

    public static class MethodTimer {
        private final long startTime;
        private final String methodTag;
        private long endTime = -1;

        private MethodTimer(String tag, long start) {
            startTime = start;
            methodTag = tag;
            times.putIfAbsent(methodTag, Sets.newConcurrentHashSet());
            times.get(methodTag).add(this);
        }

        public void stop() {
            if (endTime > 0) return;
            endTime = System.currentTimeMillis();
        }

        private long getStartTime() {
            return startTime;
        }

        private long getEndTime() {
            return endTime;
        }

        private String getMethodTag() {
            return methodTag;
        }

        private long getTimeDelta() {
            if (endTime < 0) return System.currentTimeMillis() - startTime;
            return endTime - startTime;
        }

        @Override
        public int hashCode() {
            int result = (int) (startTime ^ (startTime >>> 32));
            result = 31 * result + (int) (endTime ^ (endTime >>> 32));
            result = 31 * result + methodTag.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodTimer that = (MethodTimer) o;

            if (startTime != that.startTime) return false;
            if (endTime != that.endTime) return false;
            return methodTag.equals(that.methodTag);

        }

        @Override
        public String toString() {
            if (endTime > 0) {
                return "Method tag: " + methodTag + ", Started: " + startTime + ", Ended: " + endTime + ", Delta: " + (endTime - startTime);
            } else {
                return "Method tag: " + methodTag + ", Started: " + startTime + ". Ongoing delta: " + (System.currentTimeMillis() - startTime);
            }
        }
    }
}
