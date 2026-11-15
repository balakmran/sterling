package com.example.billing;

import java.math.BigDecimal;

public class Invoice {
    private final BigDecimal net;
    private final BigDecimal rate;

    public Invoice(BigDecimal net, BigDecimal rate) {
        this.net = net;
        this.rate = rate;
    }

    public BigDecimal tax() {
        return net.multiply(rate);
    }

    public BigDecimal gross() {
        return net.add(tax());
    }

    public boolean isFree() {
        return net.signum() == 0;
    }
}
