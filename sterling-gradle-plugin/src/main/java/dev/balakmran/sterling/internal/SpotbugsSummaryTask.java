package dev.balakmran.sterling.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.w3c.dom.Element;

/**
 * The {@code stlSecSummary} task: prints {@code spotbugsMain}'s findings on the console.
 *
 * <p>It finalizes {@code spotbugsMain} and reads its XML report, printing each finding as {@code
 * src/main/java/com/example/Foo.java:42 [SpotBugs:NP_NULL_ON_SOME_PATH] message} at error level, so
 * a failure says what's wrong without opening the HTML report. It prints at most {@value #LIMIT},
 * then a line pointing at the HTML report for the rest. It never fails the build; {@code
 * spotbugsMain} does that.
 */
@UntrackedTask(because = "It only prints the findings of a report.")
public abstract class SpotbugsSummaryTask extends DefaultTask {
  /** The most findings printed. */
  public static final int LIMIT = 50;

  /** Creates the task. Sterling registers it as {@code stlSecSummary}; Gradle instantiates it. */
  public SpotbugsSummaryTask() {}

  /**
   * An {@code onlyIf} condition: the XML report exists. A tool skips its report when it has nothing
   * to analyse.
   *
   * @return the condition
   */
  public static Spec<Task> reportExists() {
    return new ReportExists();
  }

  /**
   * {@code spotbugsMain}'s XML report.
   *
   * @return the XML report
   */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getXmlReport();

  /**
   * {@code spotbugsMain}'s HTML report, named in the line about findings not printed.
   *
   * @return the HTML report
   */
  @Internal
  public abstract RegularFileProperty getHtmlReport();

  /**
   * Main's source directories, to turn a finding's package-relative source path into a file path.
   *
   * @return the source directories
   */
  @Internal
  public abstract ConfigurableFileCollection getSourceDirectories();

  /**
   * The root project's directory, which printed paths are relative to.
   *
   * @return the root project directory
   */
  @Internal
  public abstract DirectoryProperty getRootDirectory();

  /**
   * Gradle's object factory, which instantiates the Problems API reporter.
   *
   * @return the object factory
   */
  @Inject
  protected abstract ObjectFactory getObjects();

  /** Prints the findings, and reports them through Gradle's Problems API. */
  @TaskAction
  public void print() {
    File root = getRootDirectory().get().getAsFile();
    Element bugs =
        Xml.parseReport(getXmlReport().get().getAsFile(), "SpotBugs").getDocumentElement();
    Function<String, String> path =
        SourcePaths.resolver(root, List.copyOf(getSourceDirectories().getFiles()));
    render(bugs, path, SourcePaths.relative(root.toPath(), getHtmlReport().get().getAsFile()))
        .forEach(getLogger()::error);
    ProblemReports.report(getObjects(), getLogger(), findings(bugs, path, root));
  }

  /**
   * The findings of a report, for the Problems API: all of them, not only the ones printed.
   *
   * @param bugCollection the report's {@code <BugCollection>} element
   * @param path turns a source path relative to a source directory into the path to print
   * @param root the root project's directory, which printed paths are relative to
   * @return one finding per {@code <BugInstance>}
   */
  static List<Finding> findings(Element bugCollection, Function<String, String> path, File root) {
    List<Finding> findings = new ArrayList<>();
    for (Element bug : Xml.children(bugCollection, "BugInstance")) {
      String type = bug.getAttribute("type");
      Element line = sourceLine(bug);
      String shown =
          line != null && line.hasAttribute("sourcepath")
              ? path.apply(line.getAttribute("sourcepath"))
              : null;
      // A path the lookup couldn't place is still package-relative: no file to point at.
      File file = shown == null ? null : new File(root, shown);
      boolean placed = file != null && file.isFile();
      int start =
          line != null && line.hasAttribute("start") ? parseLine(line.getAttribute("start")) : 0;
      int rank = parseLine(bug.getAttribute("rank"));
      findings.add(
          new Finding(
              type.toLowerCase(Locale.ROOT).replace('_', '-'),
              type,
              message(bug),
              placed ? file.getAbsolutePath() : null,
              placed ? start : 0,
              "SpotBugs rank "
                  + bug.getAttribute("rank")
                  + ", priority "
                  + bug.getAttribute("priority"),
              rank > 0 && rank <= 9));
    }
    return findings;
  }

  private static int parseLine(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * The lines printed for a report.
   *
   * @param bugCollection the report's {@code <BugCollection>} element
   * @param path turns a source path relative to a source directory, such as {@code
   *     com/example/Foo.java}, into the path to print
   * @param htmlReport the HTML report's path, as printed
   * @return one line per finding, at most {@value #LIMIT}, then one about the rest if any
   */
  public static List<String> render(
      Element bugCollection, Function<String, String> path, String htmlReport) {
    List<Element> bugs = Xml.children(bugCollection, "BugInstance");
    List<String> lines = new ArrayList<>();
    for (Element bug : bugs.subList(0, Math.min(LIMIT, bugs.size()))) {
      lines.add(
          location(bug, path) + " [SpotBugs:" + bug.getAttribute("type") + "] " + message(bug));
    }
    if (bugs.size() > LIMIT) {
      lines.add("... and " + (bugs.size() - LIMIT) + " more: " + htmlReport);
    }
    return lines;
  }

  // The bug's own source line, else its class's.
  private static Element sourceLine(Element bug) {
    List<Element> lines = Xml.children(bug, "SourceLine");
    Element line =
        lines.stream()
            .filter(candidate -> "true".equals(candidate.getAttribute("primary")))
            .findFirst()
            .orElse(first(lines));
    if (line == null) {
      Element type = first(Xml.children(bug, "Class"));
      line = type == null ? null : first(Xml.children(type, "SourceLine"));
    }
    return line;
  }

  private static String location(Element bug, Function<String, String> path) {
    Element line = sourceLine(bug);
    if (line == null) {
      return bug.getAttribute("type");
    }
    String file =
        line.hasAttribute("sourcepath")
            ? path.apply(line.getAttribute("sourcepath"))
            : line.getAttribute("classname");
    return line.hasAttribute("start") ? file + ":" + line.getAttribute("start") : file;
  }

  // The long message names the field or method; the short one is the fallback.
  private static String message(Element bug) {
    for (String name : List.of("LongMessage", "ShortMessage")) {
      Element message = first(Xml.children(bug, name));
      if (message != null && !message.getTextContent().isBlank()) {
        return message.getTextContent().strip();
      }
    }
    return "";
  }

  // A class rather than a lambda, so the configuration cache can store it.
  private static final class ReportExists implements Spec<Task> {
    @Override
    public boolean isSatisfiedBy(Task task) {
      return ((SpotbugsSummaryTask) task).getXmlReport().get().getAsFile().isFile();
    }
  }

  private static Element first(List<Element> elements) {
    return elements.isEmpty() ? null : elements.get(0);
  }
}
