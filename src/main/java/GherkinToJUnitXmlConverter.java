
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GherkinToJUnitXmlConverter {

    public static void main(String[] args) throws Exception {
        String featurePath = "src/test/resources/sample.feature";  // your input Gherkin feature
        String xmlOutputPath = "junit_output.xml";
        String tomlConfigPath = "src/main/java/zephyr_config.toml";

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
            }
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(xmlDoc), new StreamResult(new File(xmlOutputPath)));
        System.out.println("JUnit XML generated: " + xmlOutputPath);

        // Upload results to Zephyr
        ZephyrUploader.uploadResults(xmlOutputPath, tomlConfigPath);
    }
}
