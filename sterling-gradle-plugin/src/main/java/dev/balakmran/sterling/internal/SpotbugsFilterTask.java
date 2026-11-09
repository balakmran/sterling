package dev.balakmran.sterling.internal;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The {@code stlSpotbugsFilter} task: writes the exclude filter {@code spotbugsMain} uses.
 *
 * <p>The filter holds the {@code <Match>} elements of Sterling's default filter, then those of the
 * project's {@code sterling.spotbugs.excludeFilter} if set, then one for each class compiled from
 * generated sources. A project's filter therefore adds exclusions; it can't remove Sterling's.
 *
 * <p>Class files don't record which directory their source came from, so a filter can't match
 * {@code build/generated} by path. Instead the task reads the generated source directories when it
 * runs and turns each {@code .java} file's path into a class name, covering its nested classes too.
 */
@CacheableTask
public abstract class SpotbugsFilterTask extends DefaultTask {
  private static final String ROOT = "FindBugsFilter";
  private static final String MATCH = "Match";

  /**
   * Creates the task. Sterling registers it as {@code stlSpotbugsFilter}; Gradle instantiates it.
   */
  public SpotbugsFilterTask() {}

  /**
   * The text of Sterling's default filter, read from the plugin jar when the task is configured.
   *
   * @return the default filter's XML
   */
  @Input
  public abstract Property<String> getSterlingFilter();

  /**
   * The project's own exclude filter, if it set one.
   *
   * @return the project's filter file
   */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getConsumerFilter();

  /**
   * Directories of generated Java sources, whose classes SpotBugs skips. Missing ones are ignored.
   *
   * @return the generated source directories
   */
  @InputFiles
  @IgnoreEmptyDirectories
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getGeneratedSources();

  /**
   * Where the merged filter is written.
   *
   * @return the merged filter file
   */
  @OutputFile
  public abstract RegularFileProperty getMergedFilter();

  /**
   * Writes the merged filter.
   *
   * @throws GradleException if either filter isn't a valid SpotBugs filter
   */
  @TaskAction
  public void write() {
    String consumer = null;
    try {
      if (getConsumerFilter().isPresent()) {
        consumer = Files.readString(getConsumerFilter().get().getAsFile().toPath());
      }
      List<String> generated = generatedClasses(getGeneratedSources().getFiles());
      Files.writeString(
          getMergedFilter().get().getAsFile().toPath(),
          merge(getSterlingFilter().get(), consumer, generated));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Merges two SpotBugs exclude filters into one, and excludes the given generated classes.
   *
   * <p>Only the {@code <Match>} children of each {@code <FindBugsFilter>} root are kept, Sterling's
   * first. Either filter may declare SpotBugs's XML namespace; the result doesn't. Document type
   * declarations are rejected, so a filter can't pull in external entities. Each generated class
   * gets a {@code <Match>} on its name that also covers its nested classes.
   *
   * @param sterlingFilter the XML of Sterling's default filter
   * @param consumerFilter the XML of the project's filter, or null when it has none
   * @param generatedClasses fully qualified names of classes compiled from generated sources
   * @return the merged filter's XML
   * @throws GradleException if either filter can't be parsed or has another root element
   */
  public static String merge(
      String sterlingFilter, String consumerFilter, List<String> generatedClasses) {
    DocumentBuilder builder = secureBuilder();
    Document merged = builder.newDocument();
    Element root = merged.createElement(ROOT);
    merged.appendChild(root);
    copyMatches(builder, sterlingFilter, "Sterling's default SpotBugs filter", root);
    if (consumerFilter != null) {
      copyMatches(builder, consumerFilter, "sterling.spotbugs.excludeFilter", root);
    }
    for (String name : generatedClasses) {
      Element match = merged.createElement(MATCH);
      Element type = merged.createElement("Class");
      // A regex: the class itself, or any class nested in it.
      type.setAttribute("name", "~" + name.replace(".", "\\.") + "(\\$.*)?");
      match.appendChild(type);
      root.appendChild(match);
    }
    return serialize(merged);
  }

  /**
   * The classes declared by the {@code .java} files under some source directories, named from each
   * file's path relative to its directory. {@code package-info.java} and {@code module-info.java}
   * are skipped.
   *
   * @param sourceDirectories source directories; ones that don't exist are skipped
   * @return the fully qualified class names, sorted
   */
  public static List<String> generatedClasses(Set<File> sourceDirectories) {
    List<String> names = new ArrayList<>();
    for (File directory : sourceDirectories) {
      Path root = directory.toPath();
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(file -> file.getFileName().toString().endsWith(".java"))
            .map(file -> root.relativize(file).toString())
            .filter(path -> !path.endsWith("package-info.java"))
            .filter(path -> !path.endsWith("module-info.java"))
            .map(path -> path.substring(0, path.length() - ".java".length()))
            .map(path -> path.replace(File.separatorChar, '.'))
            .forEach(names::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    names.sort(null);
    return names;
  }

  private static void copyMatches(
      DocumentBuilder builder, String xml, String source, Element target) {
    Element filter;
    try {
      filter = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    } catch (SAXException | IOException e) {
      throw new GradleException(source + " isn't valid XML: " + e.getMessage(), e);
    }
    if (!ROOT.equals(filter.getNodeName())) {
      throw new GradleException(
          source + " must have a <" + ROOT + "> root element, not <" + filter.getNodeName() + ">.");
    }
    NodeList children = filter.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && MATCH.equals(child.getNodeName())) {
        Node copy = target.getOwnerDocument().importNode(child, true);
        dropBlankText(copy);
        target.appendChild(copy);
      }
    }
  }

  // Indentation from the source files would otherwise double up with the output's.
  private static void dropBlankText(Node node) {
    NodeList children = node.getChildNodes();
    for (int i = children.getLength() - 1; i >= 0; i--) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
        node.removeChild(child);
      } else {
        dropBlankText(child);
      }
    }
  }

  private static DocumentBuilder secureBuilder() {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("No secure XML parser available", e);
    }
  }

  private static String serialize(Document document) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      StringWriter out = new StringWriter();
      transformer.transform(new DOMSource(document), new StreamResult(out));
      return out.toString();
    } catch (TransformerException e) {
      throw new IllegalStateException("Couldn't write the merged SpotBugs filter", e);
    }
  }
}
