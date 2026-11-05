package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellingTest {

    @Test
    void distance_countsAddedRemovedChangedAndSwappedLetters() {
        assertEquals(0, Spelling.distance("high", "high"));
        assertEquals(1, Spelling.distance("hgh", "high"));
        assertEquals(1, Spelling.distance("highh", "high"));
        assertEquals(1, Spelling.distance("hogh", "high"));
        assertEquals(1, Spelling.distance("hihg", "high"));
        assertEquals(3, Spelling.distance("", "low"));
    }

    @Test
    void closest_suggestsOnlyClearlyCloseWords() {
        List<String> priorities = List.of("critical", "high", "medium", "low");

        assertEquals(Optional.of("high"), Spelling.closest("HGH", priorities));
        assertEquals(Optional.of("critical"), Spelling.closest("crtical", priorities));
        // Short words allow one mistake only.
        assertEquals(Optional.empty(), Spelling.closest("hg", priorities));
        assertEquals(Optional.empty(), Spelling.closest("urgent", priorities));
    }

    @Test
    void closest_suggestsNothing_whenTwoWordsAreEquallyClose() {
        assertEquals(Optional.empty(), Spelling.closest("lw", List.of("low", "law")));
    }
}
