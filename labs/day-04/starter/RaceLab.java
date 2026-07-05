// Day 4 lab — Concurrency I: cheap virtual threads, and a race you can see.
// Run with:  java RaceLab.java        (single-file source program — no build needed)
//
// Two experiments in one file:
//   Part A — prove blocking is cheap: launch many virtual threads that each block (sleep).
//   Part B — see a race condition: many threads increment ONE shared counter, unsafely.
//
// Virtual threads are FINAL in Java 25 — no --enable-preview needed.
// Today's goal is to MAKE THE RACE FAIL. Fixing it safely is Day 5.

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class RaceLab {

    static final int THREADS = 10_000;             // try bumping toward 1_000_000 in Part A
    static final int INCREMENTS_PER_THREAD = 1_000;

    public static void main(String[] args) throws InterruptedException {
        partA_cheapBlocking();
        System.out.println();
        partB_seeARace();
    }

    // Part A: blocking is cheap again. Every task sleeps (blocks!) 100 ms, then tallies completion.
    static void partA_cheapBlocking() throws InterruptedException {
        AtomicLong done = new AtomicLong();   // safe counter — just to confirm every task ran

        long start = System.nanoTime();

        // TODO 1: open a virtual-thread-per-task executor and submit THREADS tasks.
        //         Each task should:  Thread.sleep(100);  then  done.incrementAndGet();
        //         Hint:
        //         try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        //             for (int i = 0; i < THREADS; i++) {
        //                 executor.submit(() -> {
        //                     Thread.sleep(100);
        //                     done.incrementAndGet();
        //                     return null;
        //                 });
        //             }
        //         }   // closing the executor waits for every task to finish

        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Part A: %,d virtual threads, each blocked 100 ms — finished in %,d ms%n",
                THREADS, millis);
        System.out.printf("        completed = %,d (expected %,d)%n", done.get(), (long) THREADS);
        System.out.println("        (run one-at-a-time this would take "
                + (THREADS / 10) + " seconds — blocking is cheap now)");
    }

    // Part B: a data race. Many threads do value++ on a SHARED, unguarded field.
    static void partB_seeARace() throws InterruptedException {
        Counter counter = new Counter();
        long expected = (long) THREADS * INCREMENTS_PER_THREAD;

        // TODO 2: launch THREADS virtual threads; each calls counter.increment()
        //         INCREMENTS_PER_THREAD times. Use the same per-task executor idiom as Part A.

        long lost = expected - counter.value;
        System.out.printf("Part B: expected %,d but counter = %,d%n", expected, counter.value);
        System.out.println(lost == 0
                ? "        (no lost updates THIS run — re-run, or raise THREADS; the race is still there)"
                : String.format("        lost %,d updates ↑ that gap is a RACE CONDITION. Day 5 makes shared state safe.", lost));
    }

    // A deliberately UNSAFE counter: value++ is read-modify-write — three steps, not one.
    static final class Counter {
        long value = 0;                  // not volatile, not atomic, not synchronized — on purpose
        void increment() { value++; }    // TODO 3 (Day-5 preview): why is this line NOT atomic?
    }
}
