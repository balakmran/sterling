package dev.balakmran.sterling.internal;

import java.util.List;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Adds {@code -nowarn} to javac for an {@code stlFix} compilation.
 *
 * <p>With {@code -Werror} on, javac fails on the warnings the patched checks report before Error
 * Prone writes any change. Error Prone still prints which files it patched.
 */
public final class NoWarn implements CommandLineArgumentProvider {
  /** Creates the provider. */
  public NoWarn() {}

  @Override
  public Iterable<String> asArguments() {
    return List.of("-nowarn");
  }
}
