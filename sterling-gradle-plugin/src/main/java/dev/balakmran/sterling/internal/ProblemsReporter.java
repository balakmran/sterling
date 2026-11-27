package dev.balakmran.sterling.internal;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

/**
 * Reports findings through Gradle's Problems API, so they appear in the problems report and in
 * tools that read it.
 *
 * <p>The API is incubating. This class holds every reference to it and Gradle instantiates it, so a
 * Gradle that changed the API fails here and {@link ProblemReports} catches it, rather than the
 * task or the build failing. Gradle 9.9 deprecated {@code ProblemGroup.create}, {@code
 * ProblemId.create} and {@code severity} for {@code Problems.getGroups()}, which 9.0 doesn't have;
 * this uses the calls both have, and 9.7 and later ignore the severity.
 */
abstract class ProblemsReporter {
  private static final String GROUP = "sterling";
  private static final String GROUP_NAME = "Sterling";

  /** Creates the reporter. Gradle instantiates it and injects the services. */
  @Inject
  public ProblemsReporter() {}

  @Inject
  protected abstract Problems getProblems();

  @SuppressWarnings("deprecation") // create(..) and severity(..): deprecated since Gradle 9.9
  void report(List<Finding> findings) {
    ProblemGroup group = ProblemGroup.create(GROUP, GROUP_NAME);
    ProblemReporter reporter = getProblems().getReporter();
    for (Finding finding : findings) {
      reporter.report(
          ProblemId.create(finding.name(), finding.displayName(), group),
          spec -> {
            spec.contextualLabel(finding.message())
                .severity(finding.error() ? Severity.ERROR : Severity.WARNING);
            if (finding.file() != null && finding.line() > 0) {
              spec.lineInFileLocation(finding.file(), finding.line());
            } else if (finding.file() != null) {
              spec.fileLocation(finding.file());
            }
            if (finding.details() != null) {
              spec.details(finding.details());
            }
          });
    }
  }
}
