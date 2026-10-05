package com.crm.event_management_system.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Why {@code budget} is a BigDecimal and not a float.
 *
 * The test that matters is {@link #aFloatLosesTheCents()}, and it is deliberately
 * written as a demonstration of the old behaviour rather than as an assertion about
 * the new one. A test that only checks BigDecimal works proves nothing: it would have
 * passed before the change too, if written carelessly.
 */
class EventBudgetPrecisionTest {

    private static final String TEN_CENTS_OFF = "1000000.10";

    @Test
    @DisplayName("a budget keeps its cents exactly, through a JSON round trip")
    void bigDecimalKeepsCents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Event event = new Event();
        event.setBudget(new BigDecimal(TEN_CENTS_OFF));

        // Serialise and read back, because the budget crosses JSON on its way to
        // Angular. The interesting question is not what the getter returns but what
        // survives the trip.
        String json = mapper.writeValueAsString(event);
        Event readBack = mapper.readValue(json, Event.class);

        assertThat(readBack.getBudget())
                .as("the budget after a JSON round trip")
                .isEqualByComparingTo(TEN_CENTS_OFF);
    }

    @Test
    @DisplayName("a float cannot hold this amount, which is why it was changed")
    void aFloatLosesTheCents() {
        // This is the bug, not a test of the fix. It is here so that the reason for
        // BigDecimal is executable rather than a comment that decays.
        float asFloat = 1000000.10f;

        assertThat(BigDecimal.valueOf(asFloat))
                .as("what the old float type stored for 1000000.10")
                .isNotEqualByComparingTo(TEN_CENTS_OFF);

        // And the size of the error, so a reader can judge it rather than just being
        // told there is one.
        BigDecimal error = BigDecimal.valueOf(asFloat)
                .subtract(new BigDecimal(TEN_CENTS_OFF))
                .abs();
        assertThat(error)
                .as("the size of the error a float introduces on this amount")
                .isGreaterThan(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("scaling is what makes the column exact, not the Java type alone")
    void scaleIsTwo() {
        // BigDecimal without a scale is exact but unbounded, and Hibernate would give
        // it 65 digits. scale = 2 in the @Column is what makes DECIMAL(19,2), which
        // is the type a money column is supposed to have.
        assertThat(new BigDecimal("1234.5").setScale(2)).isEqualByComparingTo("1234.50");
        assertThat(new BigDecimal(TEN_CENTS_OFF).scale()).isEqualTo(2);
    }
}
