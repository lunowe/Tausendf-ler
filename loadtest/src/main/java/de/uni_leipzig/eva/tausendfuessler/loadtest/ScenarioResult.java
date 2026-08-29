package de.uni_leipzig.eva.tausendfuessler.loadtest;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one scenario: the NFA it checks, whether it was met, the measured numbers (in display order)
 * and free-text notes (preconditions, hints, failure reasons).
 */
public record ScenarioResult(String name, String nfa, boolean passed, Map<String, String> numbers, List<String> notes) {

    public static ScenarioResult failed(String name, String nfa, String reason) {
        return new ScenarioResult(name, nfa, false, Map.of(), List.of(reason));
    }

    public String verdict() {
        return passed ? "erfuellt" : "NICHT erfuellt";
    }

    public void print() {
        System.out.println();
        System.out.println("=== " + name + " ===");
        System.out.println("NFA: " + nfa);
        numbers.forEach((key, value) -> System.out.printf("  %-40s %s%n", key + ":", value));
        notes.forEach(note -> System.out.println("  Hinweis: " + note));
        System.out.println("Ergebnis: " + verdict());
    }
}
