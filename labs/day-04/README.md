# Day 4 — Cheap threads, and a race you can see

> Starting point: `starter/RaceLab.java`. Single-file source program — no build, no app yet.

## Objective

Feel both sides of concurrency in one small program. First prove that a flood of *blocking* virtual
threads costs almost nothing, then reproduce a **race condition** on a shared counter and watch
updates silently vanish. Today's goal is to make the race *fail reproducibly* — fixing it is Day 5.

## Concepts in play

- [What a thread really is](../../docs/content/day-04/index.md#what-a-thread-really-is)
- [ExecutorService and thread pools](../../docs/content/day-04/index.md#executorservice-and-thread-pools)
- [Virtual threads: blocking is cheap again](../../docs/content/day-04/index.md#virtual-threads-blocking-is-cheap-again)
- [The catch: shared mutable state and races](../../docs/content/day-04/index.md#the-catch-shared-mutable-state-and-races)

## Steps

1. Open `starter/RaceLab.java`. Run it as-is: `java starter/RaceLab.java`. The TODOs are unfinished,
   so both counts come out wrong (zero) — that is expected before you fill them in.
2. **Part A — cheap blocking.** In `partA_cheapBlocking()`, complete TODO 1: open a
   `newVirtualThreadPerTaskExecutor()` and submit `THREADS` tasks, each of which does
   `Thread.sleep(100)` then `done.incrementAndGet()`. Re-run: every task should finish, and the
   total wall-clock time should be a small multiple of 100 ms — *not* `THREADS × 100 ms`.
3. **Part B — see a race.** In `partB_seeARace()`, complete TODO 2: launch `THREADS` virtual threads,
   each calling `counter.increment()` `INCREMENTS_PER_THREAD` times, using the same executor idiom.
4. Re-run a few times. `counter.value` should come out **less than** the expected total, and a
   different number each run. Those are lost updates — a real race condition.
5. Read TODO 3 in the `Counter` class and be able to explain *why* `value++` is not atomic.

## Acceptance criteria

- [ ] `java starter/RaceLab.java` compiles and runs (single-file, no `--enable-preview`).
- [ ] Part A: all `THREADS` tasks complete, and the run finishes far faster than running them one at
      a time would — cheap blocking demonstrated.
- [ ] Part B: a **reproducible lost-update race** — `counter.value < expected` on at least one run.
- [ ] You can explain in one sentence why `value++` loses updates across threads.

## Stretch goals

- Bump `THREADS` toward `1_000_000` in Part A and confirm your laptop still shrugs. Then try swapping
  in `Executors.newFixedThreadPool(200)` and notice the wall-clock time get *worse* — the pool is the
  ceiling virtual threads remove.
- Make the race louder: increase `INCREMENTS_PER_THREAD` and watch the gap grow.
- *Day-5 preview (one line):* replace the plain `long value` with an `AtomicLong` and watch the lost
  updates disappear. Day 5 explains exactly why that works — don't worry about the *why* yet.
