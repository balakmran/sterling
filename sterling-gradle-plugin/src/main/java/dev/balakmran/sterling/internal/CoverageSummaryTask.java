package dev.balakmran.sterling.internal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.w3c.dom.Element;

/**
 * The {@code stlCovSummary} task: prints the coverage shortfall when the verification fails.
 *
 * <p>It finalizes {@code jacocoTestCoverageVerification} and reads {@code jacocoTestReport}'s XML
 * report, which covers the same classes. It prints the measured instruction and branch coverage
 * against the thresholds, then the {@value #TOP} source files with the most missed instructions,
 * each as {@code src/main/java/com/example/Foo.java [JaCoCo:INSTRUCTION] 12 of 40 missed}, at error
 * level. The path is the source file's, relative to the root project; a class whose source isn't
 * found keeps the package path JaCoCo reports.
 *
 * <p>It prints only when the verification failed. The verification's first action writes a marker
 * file and its last action deletes it ({@link #markFailed}, {@link #clearFailed}), so the marker is
 * left only when the verification threw. It never fails the build itself.
 */
@UntrackedTask(because = "It only prints the coverage of a report.")
public abstract class CoverageSummaryTask extends DefaultTask {
  /** The most source files printed. */
  public static final int TOP = 10;

  /** Creates the task. Sterling registers it as {@code stlCovSummary}; Gradle instantiates it. */
  public CoverageSummaryTask() {}

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
   * {@code jacocoTestReport}'s XML report.
   *
   * @return the XML report
   */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getXmlReport();

  /**
   * The marker the verification leaves when it fails.
   *
   * @return the marker file
   */
  @Internal
  public abstract RegularFileProperty getFailedMarker();

  /**
   * Main's source directories, to turn a class's package-relative source path into a file path.
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
   * The minimum instruction coverage, in percent.
   *
   * @return the instruction threshold
   */
  @Input
  public abstract Property<Integer> getInstruction();

  /**
   * The minimum branch coverage, in percent.
   *
   * @return the branch threshold
   */
  @Input
  public abstract Property<Integer> getBranch();

  /** Prints the summary if the verification failed, and removes its marker. */
  @TaskAction
  public void print() {
    File marker = getFailedMarker().get().getAsFile();
    if (!marker.exists()) {
      return;
    }
    // Consumed here, so a later run where the verification doesn't run prints nothing.
    try {
      Files.delete(marker.toPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Element report =
        Xml.parseReport(getXmlReport().get().getAsFile(), "JaCoCo").getDocumentElement();
    var path =
        SourcePaths.resolver(
            getRootDirectory().get().getAsFile(), List.copyOf(getSourceDirectories().getFiles()));
    render(report, getInstruction().get(), getBranch().get(), path).forEach(getLogger()::error);
  }

  /**
   * The lines printed for a report.
   *
   * @param report the report's {@code <report>} element
   * @param instruction the instruction threshold, in percent
   * @param branch the branch threshold, in percent
   * @param path turns a source path relative to a source directory, such as {@code
   *     com/example/Foo.java}, into the path to print; one it can't place stays as it is
   * @return the measurements, then up to {@value #TOP} source files with missed instructions; or
   *     nothing when neither measurement is below its threshold
   */
  public static List<String> render(
      Element report, int instruction, int branch, Function<String, String> path) {
    String instructionLine = measurement(report, "INSTRUCTION", "instruction", instruction);
    String branchLine = measurement(report, "BRANCH", "branch", branch);
    // The verification failed for another reason, which its own error already says.
    if (!instructionLine.contains("(below ") && !branchLine.contains("(below ")) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    lines.add("Coverage is below the thresholds:");
    lines.add(instructionLine);
    lines.add(branchLine);

    record Missed(String file, long missed, long total) {}
    List<Missed> files = new ArrayList<>();
    for (Element pkg : Xml.children(report, "package")) {
      String prefix = pkg.getAttribute("name").isEmpty() ? "" : pkg.getAttribute("name") + "/";
      for (Element source : Xml.children(pkg, "sourcefile")) {
        long[] counter = counter(source, "INSTRUCTION");
        if (counter[0] > 0) {
          files.add(
              new Missed(
                  prefix + source.getAttribute("name"), counter[0], counter[0] + counter[1]));
        }
      }
    }
    files.stream()
        .sorted(Comparator.comparingLong(Missed::missed).reversed().thenComparing(Missed::file))
        .limit(TOP)
        .forEach(file ->
            lines.add(
                path.apply(file.file())
                    + " [JaCoCo:INSTRUCTION] "
                    + file.missed()
                    + " of "
                    + file.total()
                    + " missed"));
    return lines;
  }

  private static String measurement(Element report, String type, String name, int minimum) {
    long[] counter = counter(report, type);
    long total = counter[0] + counter[1];
    if (total == 0) {
      return "  " + name + ": nothing to cover (minimum " + minimum + "%)";
    }
    // Rounded down, so a value just under the threshold never prints as equal to it.
    BigDecimal percent =
        BigDecimal.valueOf(counter[1] * 100)
            .divide(BigDecimal.valueOf(total), 1, RoundingMode.FLOOR);
    String verdict = percent.compareTo(BigDecimal.valueOf(minimum)) < 0 ? "below" : "meets";
    return "  " + name + ": " + percent + "% (" + verdict + " minimum " + minimum + "%)";
  }

  // {missed, covered} from an element's own <counter type="...">, or zeros.
  private static long[] counter(Element parent, String type) {
    for (Element counter : Xml.children(parent, "counter")) {
      if (type.equals(counter.getAttribute("type"))) {
        return new long[] {
          Long.parseLong(counter.getAttribute("missed")),
          Long.parseLong(counter.getAttribute("covered"))
        };
      }
    }
    return new long[] {0, 0};
  }

  /**
   * The verification's first action: writes the marker, which stays if the verification fails.
   *
   * @param marker the marker file
   * @return the action
   */
  public static Action<Task> markFailed(Provider<RegularFile> marker) {
    return new Marker(marker, true);
  }

  /**
   * The verification's last action, which runs only when it passed: deletes the marker.
   *
   * @param marker the marker file
   * @return the action
   */
  public static Action<Task> clearFailed(Provider<RegularFile> marker) {
    return new Marker(marker, false);
  }

  // Classes rather than lambdas, so the configuration cache can store them.
  private static final class ReportExists implements Spec<Task> {
    @Override
    public boolean isSatisfiedBy(Task task) {
      return ((CoverageSummaryTask) task).getXmlReport().get().getAsFile().isFile();
    }
  }

  private static final class Marker implements Action<Task> {
    // Resolved when the action runs, so a later change to the build directory is seen.
    private final Provider<RegularFile> marker;
    private final boolean write;

    Marker(Provider<RegularFile> marker, boolean write) {
      this.marker = marker;
      this.write = write;
    }

    @Override
    public void execute(Task task) {
      File file = marker.get().getAsFile();
      try {
        if (write) {
          Files.createDirectories(file.toPath().getParent());
          Files.writeString(file.toPath(), task.getPath() + " failed\n");
        } else {
          Files.deleteIfExists(file.toPath());
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
