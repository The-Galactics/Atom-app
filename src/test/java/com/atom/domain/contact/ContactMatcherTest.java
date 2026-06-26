package com.atom.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;

import com.atom.domain.contact.ContactMatcher.Candidate;
import com.atom.domain.contact.ContactResolution.Kind;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Pure classification for contact resolution (US-11.1 / 2B.5): an EXACT unique
 * name may be dialed directly; a single FUZZY (substring) match opens the dialer
 * for confirmation; multiple distinct fuzzy names are AMBIGUOUS (rejected).
 */
class ContactMatcherTest {

    @Test
    void exactUniqueMatch() {
        ContactResolution r = ContactMatcher.match("ana", List.of(
                new Candidate("Ana", "111"), new Candidate("Carlos", "222")));
        assertThat(r.kind()).isEqualTo(Kind.EXACT);
        assertThat(r.number()).contains("111");
    }

    @Test
    void exactPreferredOverFuzzy() {
        ContactResolution r = ContactMatcher.match("ana", List.of(
                new Candidate("Ana Lopez", "111"), new Candidate("Ana", "222")));
        assertThat(r.kind()).isEqualTo(Kind.EXACT);
        assertThat(r.number()).contains("222");
    }

    @Test
    void sameNameMultipleNumbersIsExactNotAmbiguous() {
        // One person ("Mamá") with two phone rows must NOT be rejected as ambiguous.
        ContactResolution r = ContactMatcher.match("mama", List.of(
                new Candidate("Mamá", "111"), new Candidate("Mamá", "222")));
        assertThat(r.kind()).isEqualTo(Kind.EXACT);
        assertThat(r.number()).contains("111");
    }

    @Test
    void singleFuzzyMatch() {
        ContactResolution r = ContactMatcher.match("ana", List.of(
                new Candidate("Ana Lopez", "111"), new Candidate("Carlos", "222")));
        assertThat(r.kind()).isEqualTo(Kind.FUZZY);
        assertThat(r.number()).contains("111");
    }

    @Test
    void multipleDistinctFuzzyIsAmbiguous() {
        ContactResolution r = ContactMatcher.match("an", List.of(
                new Candidate("Ana", "111"), new Candidate("Andres", "222")));
        assertThat(r.kind()).isEqualTo(Kind.AMBIGUOUS);
        assertThat(r.number()).isEmpty();
    }

    @Test
    void noMatchIsNone() {
        ContactResolution r = ContactMatcher.match("zzz", List.of(new Candidate("Ana", "111")));
        assertThat(r.kind()).isEqualTo(Kind.NONE);
        assertThat(r.number()).isEmpty();
    }

    @Test
    void blankNumbersIgnored() {
        ContactResolution r = ContactMatcher.match("ana", List.of(
                new Candidate("Ana", "  "), new Candidate("Ana Lopez", "111")));
        // Exact "Ana" has no usable number; the single fuzzy "Ana Lopez" wins.
        assertThat(r.kind()).isEqualTo(Kind.FUZZY);
        assertThat(r.number()).contains("111");
    }
}
