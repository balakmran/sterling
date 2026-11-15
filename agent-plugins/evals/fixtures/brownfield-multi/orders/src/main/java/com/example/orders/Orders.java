package com.example.orders;

import java.util.ArrayList;
import java.util.List;

public class Orders {
    private final List<String> lines = new ArrayList<>();

    public void add(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("empty line");
        }
        lines.add(line.trim());
    }

    public int count() {
        return lines.size();
    }

    public String summary() {
        if (lines.isEmpty()) {
            return "no orders";
        } else if (lines.size() == 1) {
            return "one order: " + lines.get(0);
        }
        return lines.size() + " orders";
    }

    public boolean contains(String line) {
        return lines.contains(line);
    }

    public void clear() {
        lines.clear();
    }
}
