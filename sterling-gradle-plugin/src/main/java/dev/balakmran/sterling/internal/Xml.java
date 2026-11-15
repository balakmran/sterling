package dev.balakmran.sterling.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** XML parsing that never fetches anything: no external DTDs, entities or XIncludes. */
final class Xml {
  private Xml() {}

  /**
   * A parser that rejects document type declarations outright, for files a user writes.
   *
   * @return the parser
   */
  static DocumentBuilder strictBuilder() {
    return builder(true);
  }

  /**
   * Parses a tool's report. A document type declaration is allowed, as JaCoCo's reports have one,
   * but its external DTD isn't loaded and entities aren't expanded.
   *
   * @param report the report file
   * @param tool the tool's name, for the error message
   * @return the parsed document
   * @throws GradleException if the report isn't valid XML
   */
  static Document parseReport(File report, String tool) {
    try {
      return builder(false).parse(report);
    } catch (SAXException | IOException e) {
      throw new GradleException(
          "Couldn't read " + tool + "'s report " + report + ": " + e.getMessage(), e);
    }
  }

  /**
   * The child elements of an element with a given name, in document order.
   *
   * @param parent the parent element
   * @param name the children's element name
   * @return the matching children
   */
  static List<Element> children(Element parent, String name) {
    List<Element> found = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
        found.add((Element) node);
      }
    }
    return found;
  }

  private static DocumentBuilder builder(boolean rejectDoctype) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", rejectDoctype);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("No secure XML parser available", e);
    }
  }
}
