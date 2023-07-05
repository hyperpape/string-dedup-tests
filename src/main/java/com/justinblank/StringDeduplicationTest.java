package com.justinblank;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Measures the number of strings we can have in memory at once, and how string length, uniqueness of strings, g1
 * string deduplication and explicit string deduplication affect this number.
 *
 * Note that while this is a JMH benchmark so that I can use Blackhole, the primary result is not the JMH timings, but
 * the TotalSeen line that is printed out during the test.
 */
@State(Scope.Benchmark)
public class StringDeduplicationTest {

    @Param({"0", "16", "256", "2048"})
    int stringLength;
    @Param({"1", "1024", "49152"})
    int stringCount;

    @Param({"true", "false"})
    boolean dedup;

    @Param({"true", "false"})
    boolean churnGc;

    @Param({"false"})
    boolean systemGc;

    @Param({"1048576", "65536"})
    int gcFreq;

    int seen = 0;

    List<String> preAllocatedStrings = new ArrayList<>();

    Map<String, String> stringMap = new ConcurrentHashMap<>();

    public StringDeduplicationTest() {

    }

    @Setup
    public void init() {
        for (var i = 0; i < stringCount; i++) {
            int repeatCount = Math.max(0, 4 - String.valueOf(i).length());
            String part = "0".repeat(repeatCount) + i;

            StringBuilder sb = new StringBuilder();
            while (sb.length() < stringLength) {
                sb.append(part);
            }
            preAllocatedStrings.add(sb.substring(0, Math.max(stringLength - 1, 0)));
        }
    }

    @Benchmark
    @Fork
    @BenchmarkMode(Mode.SingleShotTime)
    public List<List<String>> populateStrings(Blackhole bh) {
        try {
            if (!dedup) {
                Thread.sleep(75000);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        try {
            int accumulated = 0;
            var strings = new ArrayList<List<String>>();
            for (var i = 0; i < 1000000; i++) {
                var newStrings = new ArrayList<String>();
                for (var j = 0; j < 1000; j++) {
                    var s = copyString(bh, j);
                    if (!dedup) {
                        accumulated += s.length();
                        if (accumulated >= gcFreq) {
                            if (churnGc) {
                                churnYoungGen(bh);
                            }
                            if (systemGc) {
                                System.gc();
                            }
                            accumulated = 0;
                        }
                    }
                    seen++;
                    newStrings.add(s);
                }
                strings.add(newStrings);
                if (seen < 10000) {
                    if (seen % 100 == 0) {
                        System.out.println("CurrentSeen=" + seen + ", Time=" + new Date());
                    }
                } else if (seen % 5000 == 0) {
                    System.out.println("CurrentSeen=" + seen + ", Time=" + new Date());
                }
            }
            return strings;
        }
        catch (OutOfMemoryError e) {
            System.out.println("TotalSeen=" + seen + ", Time=" + new Date());
            return null;
        }
    }

    private void churnYoungGen(Blackhole bh) {
        for (int i = 0; i < 8192; i++) {
            byte[] bytes = new byte[1024];
            bh.consume(bytes);
        }
    }

    private String copyString(Blackhole bh, int j) {
        var b = preAllocatedStrings.get(j % preAllocatedStrings.size()).getBytes();
        bh.consume(b);
        var s = new String(b);
        if (dedup) {
            s = stringMap.computeIfAbsent(s, Function.identity());
        }
        return s;
    }

    private static void runDeduplicationTest(String dedupOption) throws RunnerException {
        // Test to determine impact of churnYoungGC vs. System.gc()
        String[] jvmArgs = new String[]{
                "",
                "-Xms128m",
                "-Xmx128m",
                "-XX:+UseG1GC",
                "-Xlog:gc,safepoint",
                "-Xlog:stringdedup=debug",
        };
        jvmArgs[0] = dedupOption;
        Options opt = new OptionsBuilder()
                .include(StringDeduplicationTest.class.getSimpleName())
                .jvmArgs(jvmArgs)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("churntest")) {
                runChurnTest();
            }
            else {
                runDeduplicationTest("-XX:+UseStringDeduplication");
                runDeduplicationTest("-XX:-UseStringDeduplication");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            System.exit(0);
        }
    }

    private static void runChurnTest() throws RunnerException {
        // Test to determine impact of churnYoungGC vs. System.gc()
        String[] jvmArgs = new String[]{
                "-XX:+UseStringDeduplication",
                "-Xms128m",
                "-Xmx128m",
                "-XX:+UseG1GC",
                "-Xlog:gc,safepoint",
                "-Xlog:stringdedup=debug",
                "-Xlog:stringdedup+phases+state=trace",
                "-Xlog:stringdedup+phases=trace",
        };
        Options opt = new OptionsBuilder()
                .include(StringDeduplicationTest.class.getSimpleName())
                .jvmArgs(jvmArgs)
                .param("stringLength", "16")
                .param("stringCount", "65536")
                .param("dedup", "false")
                .param("systemGc", "true", "false")
                .param("churnGc", "true", "false")
                .param("gcFreq", "65536")
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
