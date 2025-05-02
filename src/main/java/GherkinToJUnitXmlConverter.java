
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

        for (ScenarioDefinition scenarioDef : feature.getChildren()) {
            if (scenarioDef instanceof Scenario) {
                Scenario sc = (Scenario) scenarioDef;
                Element testcase = xmlDoc.createElement("testcase");
                testcase.setAttribute("classname", feature.getName());
                testcase.setAttribute("name", sc.getName());

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

                    for (TableRow row : rows) {
                        Element testcase = xmlDoc.createElement("testcase");
                        String exampleName = outline.getName() + " [" + row.getCells().get(0).getValue() + "]";
                        testcase.setAttribute("classname", feature.getName());
                        testcase.setAttribute("name", exampleName);

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

                        // Simulate failure based on values
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

        System.out.println("Gherkin feature converted to JUnit XML with CDATA: junit_output.xml");
    }
}
