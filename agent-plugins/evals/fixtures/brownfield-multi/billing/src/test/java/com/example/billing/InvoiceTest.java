package com.example.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceTest {
    @Test
    void addsTax() {
        Invoice invoice = new Invoice(new BigDecimal("100"), new BigDecimal("0.2"));

        assertThat(invoice.gross()).isEqualByComparingTo("120");
        assertThat(invoice.tax()).isEqualByComparingTo("20");
        assertThat(invoice.isFree()).isFalse();
    }

    @Test
    void zeroIsFree() {
        assertThat(new Invoice(BigDecimal.ZERO, BigDecimal.ONE).isFree()).isTrue();
    }
}
