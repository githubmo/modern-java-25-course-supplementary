# Day 5 — Make it safe, then make it structured

> Starting point: no reference app needed — work in `starter/`.

## Objective

Fix the race condition Day 4 left you with, then group two concurrent calls into a single unit of
work. By the end, the counter is correct on every run, and a `StructuredTaskScope` fetches two
values in parallel and combines them — cancelling the sibling if either fails.

## Concepts in play

- [Why a race even happens](../../docs/content/day-05/index.md#why-a-race-even-happens)
- [Making shared state safe: synchronized](../../docs/content/day-05/index.md#making-shared-state-safe-synchronized-and-intrinsic-locks)
- [Atomics and compare-and-set](../../docs/content/day-05/index.md#atomics-and-compare-and-set)
- [Concurrent collections](../../docs/content/day-05/index.md#concurrent-collections)
- [Structured concurrency (preview)](../../docs/content/day-05/index.md#structured-concurrency-preview)

## Steps

1. Run the starter as-is and watch the race:
   `java --enable-preview --source 25 starter/RaceLab.java`
   The `race ->` line prints a number **below** 1,000 (and it changes between runs).
2. **TODO 1 — make it safe.** Implement `runSafe()` so it returns **exactly** `THREADS` every time.
   Pick one approach and make it correct:
   - `synchronized` block around the increment,
   - an `AtomicInteger` with `incrementAndGet()`, or
   - a `ConcurrentHashMap<String, Long>` with `merge(key, 1L, Long::sum)`.
3. **TODO 2 — make it structured.** In `fetchQuote(...)`, fork a second subtask for
   `fetchPriceCents(sku)`, `join()` the scope, then combine both results into a `Quote`.
4. Re-run a few times. `safe ->` must be `1000` every time, and `quote ->` prints a combined
   `Quote[stock=…, priceCents=…]`.

> Structured concurrency is a **preview** feature in Java 25, so the run command needs both
> `--enable-preview` and `--source 25`. Virtual threads and scoped values are final — no flag for
> those.

## Acceptance criteria

- [ ] `runSafe()` returns exactly `THREADS` on every run (no wobble).
- [ ] The fix uses `synchronized`, an atomic, or a concurrent collection — not `volatile` alone.
- [ ] `fetchQuote(...)` forks both subtasks and combines them after `join()`.
- [ ] If one subtask throws, the scope cancels the other and `join()` propagates the failure.

## Stretch goals

- Make one of the two fetches throw, and confirm the sibling is cancelled (no leaked work).
- Swap `AtomicInteger` for `LongAdder` and reason about when each is the better fit.
- Bind a `ScopedValue` correlation id around `fetchQuote` and read it inside a subtask — proving the
  binding is inherited by forked subtasks.
