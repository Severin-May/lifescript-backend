package com.lifescript.compiler;

import java.util.Collection;
import java.util.Optional;

// "Did you mean ...?" hints: the candidate fewest typing mistakes away from the word.
final class Spelling {

    private Spelling() {
    }

    // Only close candidates count (1 mistake in words up to 4 letters, else 2); a tie suggests nothing.
    static Optional<String> closest(String word, Collection<String> candidates) {
        String lower = word.toLowerCase();
        int allowed = lower.length() <= 4 ? 1 : 2;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String candidate : candidates) {
            int distance = distance(lower, candidate.toLowerCase());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
                tie = false;
            } else if (distance == bestDistance) {
                tie = true;
            }
        }
        return best != null && bestDistance <= allowed && !tie ? Optional.of(best) : Optional.empty();
    }

    // Damerau-Levenshtein (optimal string alignment): adding, removing or changing a letter,
    // or swapping two neighbouring letters, is one mistake each.
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int change = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + change);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
