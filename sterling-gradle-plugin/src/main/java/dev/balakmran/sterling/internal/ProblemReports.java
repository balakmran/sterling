package dev.balakmran.sterling.internal;

import java.util.List;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.ObjectInstantiationException;

/**
 * Reports findings through Gradle's incubating Problems API without ever failing the build.
 *
 * <p>The reporter is created inside the {@code try}, so a Gradle whose API differs from the one
 * Sterling was built against fails at instantiation (an {@link ObjectInstantiationException}) or at
 * the first call ({@link LinkageError}), and is logged at info level instead.
 */
final class ProblemReports {
  private ProblemReports() {}

  /**
   * Reports the findings, if the API is there and works.
   *
   * @param objects the task's object factory
   * @param logger the task's logger
   * @param findings what to report
   */
  static void report(ObjectFactory objects, Logger logger, List<Finding> findings) {
    if (findings.isEmpty()) {
      return;
    }
    try {
      objects.newInstance(ProblemsReporter.class).report(findings);
    } catch (LinkageError | ObjectInstantiationException e) {
      logger.info(
          "Gradle's Problems API isn't available here; the console lines are unaffected.", e);
    }
  }
}
