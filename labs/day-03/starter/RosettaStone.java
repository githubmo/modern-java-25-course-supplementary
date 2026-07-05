// Day 3 lab — Rosetta Stone starter (Java 25).
//
// Run with:   java starter/RosettaStone.java
//   (a "compact source file" — no build, no class wrapper, no `public static void main`.
//    `void main()` and `IO.println` are final in JDK 25. This file runs as-is.)
//
// Goal: translate the idioms you reach for in your language into idiomatic Java 25.
// Work the TODOs in order. After each one, UNCOMMENT its line(s) in main() and re-run.
// Rules of the day: keep illegal states unrepresentable, prefer Optional over null,
// and never use String Templates (STR."..." does not exist in Java 25 — use formatted(...)).

import java.util.List;
import java.util.Map;
import java.util.Optional;

// ---------------------------------------------------------------------------
// TODO 1 — Records: model Money as an immutable value object.
//          Add a compact constructor that rejects negative cents, so a negative
//          Money can never be built. (C# record / Python @dataclass(frozen=True).)
//
//   record Money(long cents, String currency) {
//       Money {
//           // TODO: throw IllegalArgumentException if cents < 0
//       }
//   }
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// TODO 2 — Sealed types: model OrderEvent as a CLOSED set of cases.
//          A sealed interface permitting exactly three records.
//          (TypeScript discriminated union / Rust enum / F# sum type.)
//
//   sealed interface OrderEvent permits OrderPlaced, OrderPaid, OrderCancelled {}
//   record OrderPlaced(String sku, int quantity) implements OrderEvent {}
//   record OrderPaid(String txnId)               implements OrderEvent {}
//   record OrderCancelled(String reason)         implements OrderEvent {}
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// TODO 3 — Pattern matching: describe an OrderEvent with an EXHAUSTIVE switch
//          using record patterns and NO default branch. Let the compiler enforce
//          completeness — delete a case and watch it refuse to compile.
//
//   String describe(OrderEvent event) {
//       return switch (event) {
//           case OrderPlaced(var sku, var qty) -> "placed %d x %s".formatted(qty, sku);
//           // TODO: handle OrderPaid and OrderCancelled
//       };
//   }
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// TODO 4 — Text block: render an OrderPlaced as multi-line JSON with a text block
//          and formatted(...). (No String Templates.)
//
//   String orderJson(OrderPlaced o) {
//       return """
//           { "sku": "%s", "qty": %d }
//           """.formatted(o.sku(), o.quantity());
//   }
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// TODO 5 — Stream: total the units across a list of OrderPlaced with a lazy
//          filter/map pipeline and a terminal operation.
//
//   int totalUnits(List<OrderPlaced> orders) {
//       return orders.stream()
//               .filter(o -> o.quantity() > 0)
//               .mapToInt(OrderPlaced::quantity)
//               .sum();
//   }
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// TODO 6 — Optional: look up a role for a user and fall back to a default,
//          WITHOUT a null check. (Java's null is not in the type system.)
//
//   String roleFor(Map<String, String> roles, String user) {
//       return Optional.ofNullable(roles.get(user)).orElse("guest");
//   }
// ---------------------------------------------------------------------------

void main() {
    IO.println("Rosetta Stone — implement the TODOs, uncomment their lines, and re-run.");
    IO.println("Checklist:");
    IO.println("  [ ] TODO 1  record Money + validation");
    IO.println("  [ ] TODO 2  sealed OrderEvent + 3 records");
    IO.println("  [ ] TODO 3  describe(): exhaustive switch, no default");
    IO.println("  [ ] TODO 4  orderJson(): text block + formatted");
    IO.println("  [ ] TODO 5  totalUnits(): stream pipeline");
    IO.println("  [ ] TODO 6  roleFor(): Optional, no null");
    IO.println("");

    // Warm-up (already done for you): an Optional from a Stream, no null in sight.
    List<String> currencies = List.of("USD", "EUR", "GBP");
    Optional<String> eur = currencies.stream().filter("EUR"::equals).findFirst();
    IO.println("warm-up — first EUR: " + eur.orElse("none"));

    // === Uncomment each line as you finish the matching TODO, then re-run ===
    // var price = new Money(1299, "USD");
    // IO.println("TODO 1 — " + price);                                  // value object + toString
    // IO.println("TODO 3 — " + describe(new OrderPlaced("BOOK-1", 3))); // exhaustive switch
    // IO.println("TODO 4 — " + orderJson(new OrderPlaced("BOOK-1", 3)));// text block
    // IO.println("TODO 5 — total units: "
    //         + totalUnits(List.of(new OrderPlaced("A", 3), new OrderPlaced("B", 5))));
    // IO.println("TODO 6 — role: " + roleFor(Map.of("ada", "admin"), "guest"));
}
