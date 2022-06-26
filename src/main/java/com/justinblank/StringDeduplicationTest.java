package com.justinblank;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@State(Scope.Benchmark)
public class StringDeduplicationTest {

    @Param({"0", "16", "256", "65536"})
    int stringLength;
    @Param({"1", "1024", "65536"})
    int stringCount;

    @Param({"true", "false"})
    boolean dedup;

    int seen = 0;

    List<String> strings = new ArrayList<>();


    Map<String, String> stringMap = new ConcurrentHashMap<>();

    public StringDeduplicationTest() {

    }

    @Setup
    public void init() {
        if (stringLength * stringCount > (1024 * 1024 * 1024)) {
            System.out.println("No results");
            System.exit(-1);
        }

        for (var i = 0; i < stringCount; i++) {
            int repeatCount = Math.max(0, 4 - String.valueOf(i).length());
            String part = "0".repeat(repeatCount) + i;

            StringBuilder sb = new StringBuilder();
            while (sb.length() < stringLength) {
                sb.append(part);
            }
            strings.add(sb.substring(0, Math.max(stringLength - 1, 0)));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public List<List<String>> populateStrings(Blackhole bh) {
        try {
            var strings = new ArrayList<List<String>>();
            for (var i = 0; i < 1000000; i++) {
                var newStrings = new ArrayList<String>();
                for (var j = 0; j < 1000; j++) {
                    var s = getString(bh, j);
                    seen++;
                    newStrings.add(s);
                }
                strings.add(newStrings);
                if (seen % 5000 == 0) {
                    System.out.println("CurrentSeen=" + seen + ", Time=" + new Date());
                }
            }
            return strings;
        }
        catch (OutOfMemoryError e) {
            System.out.println("TotalSeen=" + seen);
            throw e;
        }
    }

    private String getString(Blackhole bh, int j) {
        var b = strings.get(j % strings.size()).getBytes();
        bh.consume(b);
        var s = new String(b);
        if (dedup) {
            s = stringMap.computeIfAbsent(s, Function.identity());
        }
        return s;
    }

    public static void main(String[] args) {
        try {
            Options opt = new OptionsBuilder()
                    .include(StringDeduplicationTest.class.getSimpleName())
                .jvmArgs("-Xms256m", "-Xmx256m", "-XX:+UseStringDeduplication", "-XX:+UseG1GC")
                    .forks(1)
                    .build();

            new Runner(opt).run();

            opt = new OptionsBuilder()
                    .include(StringDeduplicationTest.class.getSimpleName())
                .jvmArgs("-Xms256m", "-Xmx256m", "-XX:-UseStringDeduplication", "-XX:+UseG1GC")
                    .forks(1)
                    .build();

            new Runner(opt).run();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            System.exit(0);
        }
    }
}
