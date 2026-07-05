// Day 2 lab — concurrent CSV pipeline starter.
// Run with:  java SalesPipeline.java data
//
// Read every *.csv in the given directory concurrently (one virtual thread per file),
// parse rows "sku,quantity,unitPriceCents", and print per-SKU totals.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class SalesPipeline {

    record Row(String sku, int quantity, long unitPriceCents) {}
    record Totals(long quantity, long revenueCents) {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "data");

        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".csv")).sorted().toList();
        }

        // TODO 1: submit one parse(file) task per file to a virtual-thread executor and
        //         collect all rows. Hint:
        // try (var executor = Executors.newVirtualThreadPerTaskExecutor()) { ... }
        List<Row> allRows = List.of(); // replace me

        // TODO 2: aggregate per-SKU quantity and revenue (unitPriceCents * quantity).
        Map<String, Totals> bySku = new TreeMap<>();

        bySku.forEach((sku, t) -> System.out.printf(
                "%-8s qty=%-6d revenue=%.2f%n", sku, t.quantity(), t.revenueCents() / 100.0));
    }

    // TODO 3: finish the parser — skip blank or malformed lines instead of throwing.
    static List<Row> parse(Path file) throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String[] parts = line.split(",");
            if (parts.length != 3) continue;
            try {
                rows.add(new Row(parts[0].trim(),
                        Integer.parseInt(parts[1].trim()),
                        Long.parseLong(parts[2].trim())));
            } catch (NumberFormatException ignored) {
                // header row or malformed data — skip it
            }
        }
        return rows;
    }
}
