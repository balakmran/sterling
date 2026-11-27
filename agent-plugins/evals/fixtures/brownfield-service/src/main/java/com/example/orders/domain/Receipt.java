package com.example.orders.domain;

public class Receipt {
    private final String id;

    public Receipt(String id) { this.id = id; }

    public String toString() {
        return "Receipt " + id;
    }
}
