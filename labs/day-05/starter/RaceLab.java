// Day 5 lab — Java Concurrency II.
//
// Part 1: make a racing counter correct.
// Part 2: treat two concurrent calls as ONE unit with a StructuredTaskScope.
//
// Structured concurrency is a PREVIEW feature in JDK 25, so run with:
//     java --enable-preview --source 25 RaceLab.java
//
// (Virtual threads and scoped values are FINAL — they need no flag. The flag here
//  is only because Part 2 uses the preview StructuredTaskScope API.)

import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

public class RaceLab {

    static final int THREADS = 1_000;

    // ---- Part 1: the race (Day 4's cliffhanger) ---------------------------

    // A plain mutable counter — NOT safe to touch from many threads at once.
    static int unsafeCounter = 0;

    static void bumpUnsafe() {
        unsafeCounter++;            // read-modify-write: three steps, not one
    }

    // Runs THREADS increments concurrently with no synchronisation.
    // Returns a number that is usually LESS than THREADS — updates get lost.
    static int runRace() {
        unsafeCounter = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < THREADS; i++) {
                executor.submit(RaceLab::bumpUnsafe);
            }
        } // executor.close() waits for every task to finish
        return unsafeCounter;
    }

    // TODO 1: make the count correct under concurrency. Pick ONE strategy:
    //   (a) wrap the increment in a synchronized block on a private lock object,
    //   (b) replace the int with an AtomicInteger and call incrementAndGet(),
    //   (c) count into a ConcurrentHashMap<String,Long> with merge(k, 1L, Long::sum).
    // Implement runSafe() so it ALWAYS returns exactly THREADS, every run.
    static int runSafe() {
        // Replace this stub. (Starting from runRace() but unchanged, it still races.)
        return runRace();
    }

    // ---- Part 2: structured concurrency (PREVIEW) ------------------------

    record Quote(int stock, int priceCents) {}

    static int fetchStock(String sku) throws InterruptedException {
        Thread.sleep(200);          // pretend this is a slow network call
        return 42;
    }

    static int fetchPriceCents(String sku) throws InterruptedException {
        Thread.sleep(200);          // and so is this — they should run in parallel
        return 1999;
    }

    // Fan out two slow calls and combine them as ONE unit of work: if either
    // subtask fails, the scope cancels the other and join() throws.
    static Quote fetchQuote(String sku) throws InterruptedException {
        try (var scope = StructuredTaskScope.open()) {     // default joiner: all must succeed
            Subtask<Integer> stock = scope.fork(() -> fetchStock(sku));

            // TODO 2: fork a second subtask for fetchPriceCents(sku), then join()
            //         the scope and combine both results into the returned Quote.
            scope.join();
            return new Quote(stock.get(), 0 /* replace 0 with the price subtask's result */);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("race  -> " + runRace() + "   (expected " + THREADS + ", usually less)");
        System.out.println("safe  -> " + runSafe() + "   (must be exactly " + THREADS + ")");
        System.out.println("quote -> " + fetchQuote("SKU-1"));
    }
}
