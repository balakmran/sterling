package dev.balakmran.sterling;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Settings for test coverage with JaCoCo.
 *
 * <p>Coverage is measured on the {@code test} task. After it runs, {@code jacocoTestReport} writes
 * HTML and XML reports, and {@code stlCov} fails when coverage is below either threshold. JaCoCo
 * already ignores classes and methods annotated with an annotation named {@code *Generated*} that
 * is kept in the class file, such as Lombok's {@code @Generated}.
 */
public abstract class CoverageSettings {
  /** Creates the settings. Gradle instantiates this class for the {@code sterling} extension. */
  public CoverageSettings() {}

  /**
   * Whether tests record coverage and {@code stlCov} verifies it.
   *
   * <p>Defaults to false for {@link Profile#LENIENT} and true otherwise. When false, no test task
   * gets the JaCoCo agent, so no coverage data or report is produced, and a JDK that JaCoCo doesn't
   * support yet can't break the tests.
   *
   * @return the enabled property
   */
  public abstract Property<Boolean> getEnabled();

  /**
   * The minimum share of bytecode instructions the tests must execute, in percent.
   *
   * <p>Defaults to 0, 80 and 95 for {@link Profile#LENIENT}, {@link Profile#STANDARD} and {@link
   * Profile#STRICT}. A value outside 0 to 100 fails the build with a message naming this setting.
   *
   * @return the instruction threshold
   */
  public abstract Property<Integer> getInstruction();

  /**
   * The minimum share of branches ({@code if}, {@code switch} and similar) the tests must take, in
   * percent.
   *
   * <p>Defaults to 0, 70 and 85 for {@link Profile#LENIENT}, {@link Profile#STANDARD} and {@link
   * Profile#STRICT}. A value outside 0 to 100 fails the build with a message naming this setting.
   *
   * @return the branch threshold
   */
  public abstract Property<Integer> getBranch();

  /**
   * Class files left out of coverage, as Ant-style patterns. Empty by default.
   *
   * <p>Patterns match paths under the classes directory, such as <code>**&#47;*Application*</code>
   * for a Spring Boot main class. They apply to the report and the verification alike.
   *
   * <p>End a pattern with {@code *}: nested and anonymous classes compile to their own class files,
   * such as {@code Application$1.class}, which {@code Application.class} alone doesn't match.
   *
   * @return the exclude patterns
   */
  public abstract ListProperty<String> getExcludes();
}
