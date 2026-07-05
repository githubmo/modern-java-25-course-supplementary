# Day 3 — Java for Polyglots (the Rosetta Stone)

> Starting point: no reference app yet — work in `starter/`. Run with the single-file launcher,
> no build needed.

## Objective

Translate the idioms you reach for in your primary language (C#, TypeScript, Python, Ruby, or Go)
into idiomatic Java 25, then peer-review them in pairs. By the end, `starter/RosettaStone.java`
should model a small domain with records and a sealed type, branch on it with an exhaustive
`switch`, and use a text block, a Stream, and an `Optional` — with no `null` as control flow and no
String Templates.

## Concepts in play

- [How Java runs](../../docs/content/day-03/index.md#how-java-runs)
- [Records and sealed types](../../docs/content/day-03/index.md#modeling-data-records-and-sealed-types)
- [Pattern matching over a closed set](../../docs/content/day-03/index.md#pattern-matching-switch-over-the-closed-set)
- [The standard library, the modern way](../../docs/content/day-03/index.md#the-standard-library-the-modern-way)

## Steps

1. Open `starter/RosettaStone.java`. It runs out of the box — `java starter/RosettaStone.java` —
   and prints a checklist. Each `// TODO` marks an idiom to express in Java 25. Implement them in
   order and re-run after each one.
2. **Records (TODO 1).** Model `Money` as an immutable record with a *compact constructor* that
   rejects negative `cents`. Make illegal states unrepresentable.
3. **Sealed type (TODO 2).** Model `OrderEvent` as a closed set: a sealed interface permitting the
   records `OrderPlaced`, `OrderPaid`, and `OrderCancelled`.
4. **Exhaustive switch (TODO 3).** Write `describe(OrderEvent)` with an exhaustive `switch` using
   record patterns and **no `default`** — let the compiler enforce completeness. Prove it: delete a
   `case` and watch it stop compiling, then put it back.
5. **Text block (TODO 4).** Build a multi-line JSON string with a text block + `formatted(...)`.
   Never String Templates (`STR."..."` does not exist in Java 25).
6. **Stream (TODO 5).** From a list of `OrderPlaced`, compute the total quantity with a
   `filter`/`map`/reduce pipeline.
7. **Optional (TODO 6).** Look up a currency and supply a fallback with `Optional`, with no `null`
   check.
8. Wire each finished TODO into `main` (uncomment the demo lines as you go) and re-run.
9. Pair up and review: did your partner make illegal states unrepresentable? Is there any stray
   `null` or `default`?

## Acceptance criteria

- [ ] `java starter/RosettaStone.java` compiles and runs.
- [ ] A record with a validating compact constructor (a negative `Money` cannot be built).
- [ ] At least one sealed interface consumed by an exhaustive `switch` (no `default`).
- [ ] One text block with `formatted(...)`; **no** String Templates anywhere.
- [ ] One Stream pipeline and one `Optional` used instead of a `null` check.

## Stretch goals

- Add a guarded pattern (`when`) and a nested record pattern.
- Add a `java.time` step: compute an expiry `Instant` from `Instant.now()` plus a `Duration`.
- Express one idiom two ways and argue which reads better in Java.
- Rewrite the program *without* the compact-source form — a normal `public class` with
  `public static void main(String[] args)` — and note what ceremony the compact form removed.
