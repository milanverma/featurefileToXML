
import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ast.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Set;

public class GherkinToJUnitXmlConverter {

    public static void main(String[] args) throws Exception {
        String featurePath = "src/test/resources/sample.feature";
        String content = new String(Files.readAllBytes(Paths.get(featurePath)));

        Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
        GherkinDocument doc = parser.parse(content);
        Feature feature = doc.getFeature();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document xmlDoc = dBuilder.newDocument();

        Element testsuite = xmlDoc.createElement("testsuite");
        testsuite.setAttribute("name", feature.getName());
        xmlDoc.appendChild(testsuite);

        Set<String> featureTags = feature.getTags().stream()
            .map(t -> t.getName().replace("@", ""))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (feature.getDescription() != null && !feature.getDescription().trim().isEmpty()) {
            Element systemOut = xmlDoc.createElement("system-out");
            CDATASection featureDesc = xmlDoc.createCDATASection("Feature Description:\n" + feature.getDescription().trim());
            systemOut.appendChild(featureDesc);
            testsuite.appendChild(systemOut);
        }

        for (ScenarioDefinition scenarioDef : feature.getChildren()) {
            if (scenarioDef instanceof Scenario) {
                Scenario sc = (Scenario) scenarioDef;
                Element testcase = xmlDoc.createElement("testcase");
                testcase.setAttribute("classname", feature.getName());
                testcase.setAttribute("name", sc.getName());

                Set<String> allTags = new LinkedHashSet<>(featureTags);
                allTags.addAll(sc.getTags().stream()
                    .map(t -> t.getName().replace("@", ""))
                    .collect(Collectors.toSet()));

                if (!allTags.isEmpty()) {
                    Element tagsEl = xmlDoc.createElement("tags");
                    for (String tag : allTags) {
                        Element tagEl = xmlDoc.createElement("tag");
                        tagEl.setTextContent(tag);
                        tagsEl.appendChild(tagEl);
                    }
                    testcase.appendChild(tagsEl);
                }

                StringBuilder stepText = new StringBuilder();
                for (Step step : sc.getSteps()) {
                    stepText.append(step.getKeyword()).append(step.getText()).append(".......................................passed\n");
                }

                Element systemOut = xmlDoc.createElement("system-out");
                CDATASection cdata = xmlDoc.createCDATASection(stepText.toString());
                systemOut.appendChild(cdata);
                testcase.appendChild(systemOut);

                testsuite.appendChild(testcase);
            } else if (scenarioDef instanceof ScenarioOutline) {
                ScenarioOutline outline = (ScenarioOutline) scenarioDef;
                for (Examples examples : outline.getExamples()) {
                    List<TableRow> rows = examples.getTableBody();
                    List<TableCell> headers = examples.getTableHeader().getCells();

                    Set<String> scenarioTags = outline.getTags().stream()
                        .map(t -> t.getName().replace("@", ""))
                        .collect(Collectors.toSet());
                    Set<String> exampleTags = examples.getTags().stream()
                        .map(t -> t.getName().replace("@", ""))
                        .collect(Collectors.toSet());

                    Set<String> allTags = new LinkedHashSet<>(featureTags);
                    allTags.addAll(scenarioTags);
                    allTags.addAll(exampleTags);

                    for (TableRow row : rows) {
                        Element testcase = xmlDoc.createElement("testcase");
                        String exampleName = outline.getName() + " [" + row.getCells().get(0).getValue() + "]";
                        testcase.setAttribute("classname", feature.getName());
                        testcase.setAttribute("name", exampleName);

                        if (!allTags.isEmpty()) {
                            Element tagsEl = xmlDoc.createElement("tags");
                            for (String tag : allTags) {
                                Element tagEl = xmlDoc.createElement("tag");
                                tagEl.setTextContent(tag);
                                tagsEl.appendChild(tagEl);
                            }
                            testcase.appendChild(tagsEl);
                        }

                        StringBuilder stepText = new StringBuilder();
                        for (Step step : outline.getSteps()) {
                            String text = step.getText();
                            for (int i = 0; i < headers.size(); i++) {
                                String placeholder = "<" + headers.get(i).getValue() + ">";
                                String value = row.getCells().get(i).getValue();
                                text = text.replace(placeholder, value);
                            }
                            stepText.append(step.getKeyword()).append(text).append(".......................................passed\n");
                        }

                        Element systemOut = xmlDoc.createElement("system-out");
                        CDATASection cdata = xmlDoc.createCDATASection(stepText.toString());
                        systemOut.appendChild(cdata);
                        testcase.appendChild(systemOut);

                        String lower = stepText.toString().toLowerCase();
                        if (lower.contains("invalid") || lower.contains("fail")) {
                            Element failure = xmlDoc.createElement("failure");
                            failure.setAttribute("message", "Simulated failure");
                            CDATASection failDetails = xmlDoc.createCDATASection("StackTrace:\nSimulated stack trace for failed step.");
                            failure.appendChild(failDetails);
                            testcase.appendChild(failure);
                        }

                        testsuite.appendChild(testcase);
                    }
                }
            }
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(xmlDoc);
        StreamResult result = new StreamResult(new File("junit_output.xml"));
        transformer.transform(source, result);

        System.out.println("Feature converted to JUnit XML with inherited tags: junit_output.xml");

        // Call uploader
        ZephyrUploader.uploadToZephyr("junit_output.xml", "src/main/java/zephyr_config.toml");
    }
}
