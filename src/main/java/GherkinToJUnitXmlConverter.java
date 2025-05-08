import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ast.*;
import com.moandjiezana.toml.Toml;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class GherkinToJUnitXmlConverter {

    public static void main(String[] args) throws Exception {
        String featurePath = null;
        String configPath = "user_config.toml";
        String outputPath = "junit_output.xml";

        // --- CLI Arguments Parsing ---
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--feature":
                    featurePath = args[++i];
                    break;
                case "--config":
                    configPath = args[++i];
                    break;
                case "--output":
                    outputPath = args[++i];
                    break;
                default:
                    System.out.println("Unknown argument: " + args[i]);
            }
        }

        if (featurePath == null) {
            System.err.println("Error: --feature argument is required.");
            return;
        }

        // --- Read Feature File ---
        String content = new String(Files.readAllBytes(Paths.get(featurePath)));
        Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
        GherkinDocument doc = parser.parse(content);
        Feature feature = doc.getFeature();

        // --- Load TOML Config ---
        Toml toml = new Toml();
        File tomlFile = new File(configPath);
        if (tomlFile.exists()) {
            toml = toml.read(tomlFile);
        }

        Toml behavior = toml.getTable("behavior");
        boolean markAllPassed = behavior != null && behavior.getBoolean("mark_all_passed", true);
        String extraTag = behavior != null ? behavior.getString("extra_tag") : null;
        boolean includeTimestamp = behavior != null && behavior.getBoolean("include_timestamp", false);
        boolean featureAsClassName = behavior == null || behavior.getBoolean("feature_name_as_classname", true);
        boolean uploadRequested = behavior != null && behavior.getBoolean("upload", false);

        // --- Build XML ---
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
                processScenario(xmlDoc, testsuite, feature, (Scenario) scenarioDef, featureTags,
                        markAllPassed, extraTag, includeTimestamp, featureAsClassName);
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
                    if (extraTag != null && !extraTag.isEmpty()) allTags.add(extraTag);

                    for (TableRow row : rows) {
                        Element testcase = xmlDoc.createElement("testcase");
                        String exampleName = outline.getName() + " [" + row.getCells().get(0).getValue() + "]";
                        testcase.setAttribute("classname", featureAsClassName ? feature.getName() : "DefaultClass");
                        testcase.setAttribute("name", exampleName);

                        if (includeTimestamp) {
                            testcase.setAttribute("timestamp", Instant.now().toString());
                        }

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
                                text = text.replace("<" + headers.get(i).getValue() + ">", row.getCells().get(i).getValue());
                            }
                            stepText.append(step.getKeyword()).append(text).append(".......................................passed\n");
                        }

                        Element systemOut = xmlDoc.createElement("system-out");
                        CDATASection cdata = xmlDoc.createCDATASection(stepText.toString());
                        systemOut.appendChild(cdata);
                        testcase.appendChild(systemOut);

                        if (!markAllPassed) {
                            String lower = stepText.toString().toLowerCase();
                            if (lower.contains("fail") || lower.contains("invalid")) {
                                Element failure = xmlDoc.createElement("failure");
                                failure.setAttribute("message", "Simulated failure");
                                CDATASection failDetails = xmlDoc.createCDATASection("StackTrace:\nSimulated failure details.");
                                failure.appendChild(failDetails);
                                testcase.appendChild(failure);
                            }
                        }

                        testsuite.appendChild(testcase);
                    }
                }
            }
        }

        // --- Write Output XML ---
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(xmlDoc);
        StreamResult result = new StreamResult(new File(outputPath));
        transformer.transform(source, result);

        System.out.println("✅ Feature converted to JUnit XML: " + outputPath);

        // --- Handle Upload Option ---
        //replace below with:
        // ZephyrUploader.uploadToZephyr(outputPath, configPath);
        //Once services engagement or online capablity is need.
        if (uploadRequested) {

            System.out.println("ℹ️  Upload to Zephyr or other tools is a premium feature.");
            System.out.println("📩 Please contact trigenica365@gmail.com to enable integrations.");
        }
    }

    private static void processScenario(Document xmlDoc, Element testsuite, Feature feature, Scenario sc,
                                        Set<String> featureTags, boolean markAllPassed, String extraTag,
                                        boolean includeTimestamp, boolean featureAsClassName) {
        Element testcase = xmlDoc.createElement("testcase");
        testcase.setAttribute("classname", featureAsClassName ? feature.getName() : "DefaultClass");
        testcase.setAttribute("name", sc.getName());

        if (includeTimestamp) {
            testcase.setAttribute("timestamp", Instant.now().toString());
        }

        Set<String> allTags = new LinkedHashSet<>(featureTags);
        allTags.addAll(sc.getTags().stream()
                .map(t -> t.getName().replace("@", ""))
                .collect(Collectors.toSet()));
        if (extraTag != null && !extraTag.isEmpty()) allTags.add(extraTag);

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

        if (!markAllPassed) {
            String lower = stepText.toString().toLowerCase();
            if (lower.contains("fail") || lower.contains("invalid")) {
                Element failure = xmlDoc.createElement("failure");
                failure.setAttribute("message", "Simulated failure");
                CDATASection failDetails = xmlDoc.createCDATASection("StackTrace:\nSimulated failure details.");
                failure.appendChild(failDetails);
                testcase.appendChild(failure);
            }
        }

        testsuite.appendChild(testcase);
    }
}
