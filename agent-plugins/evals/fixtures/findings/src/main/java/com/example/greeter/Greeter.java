package com.example.greeter;

import org.jspecify.annotations.Nullable;

/** Builds greetings. */
public final class Greeter {
  private final String greeting;

  public Greeter(String greeting) {
    this.greeting = greeting;
  }

  /** Greets someone, or the world when no name is given. */
  public String greet(@Nullable String name) {
    if (name == null) {
      return greeting + ", world";
    }
    return greeting + ", " + name;
  }

  /** The length of a name, or zero when there is none. */
  public int nameLength(@Nullable String name) {
    return name.length();
  }

  /** Whether two sets of greeting parts are the same. */
  public boolean sameParts(String[] first, String[] second) {
    return first.equals(second);
  }
}
