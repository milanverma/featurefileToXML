
import io.cucumber.gherkin.Gherkin;
import io.cucumber.messages.IdGenerator;
import io.cucumber.messages.types.*;
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
        String featurePath = "src/test/resources/feature.feature"; // path to your .feature file
        List<Envelope> envelopes = Gherkin.fromPaths(
                List.of(Paths.get(featurePath)),
                false,
                true,
                true,
                new IdGenerator.Incrementing()
        );

        GherkinDocument doc = envelopes.stream()
                .filter(e -> e.getGherkinDocument().isPresent())
                .map(e -> e.getGherkinDocument().get())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No Gherkin document found"));

        Feature feature = doc.getFeature().orElseThrow();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document xmlDoc = dBuilder.newDocument();

        Element testsuite = xmlDoc.createElement("testsuite");
        testsuite.setAttribute("name", feature.getName());
        xmlDoc.appendChild(testsuite);

        for (FeatureChild child : feature.getChildren()) {
            if (child.getScenario().isPresent()) {
                Scenario scenario = child.getScenario().get();

                Element testcase = xmlDoc.createElement("testcase");
                testcase.setAttribute("classname", feature.getName());
                testcase.setAttribute("name", scenario.getName());

                for (Step step : scenario.getSteps()) {
                    Element stepElement = xmlDoc.createElement("system-out");
                    stepElement.setTextContent(step.getKeyword() + step.getText());
                    testcase.appendChild(stepElement);

                    if (step.getDataTable().isPresent()) {
                        Element dataTable = xmlDoc.createElement("system-out");
                        StringBuilder tableText = new StringBuilder("DataTable:\n");
                        for (TableRow row : step.getDataTable().get().getRows()) {
                            for (TableCell cell : row.getCells()) {
                                tableText.append(cell.getValue()).append(" | ");
                            }
                            tableText.append("\n");
                        }
                        dataTable.setTextContent(tableText.toString());
                        testcase.appendChild(dataTable);
                    }
                }

                testsuite.appendChild(testcase);
            }
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(xmlDoc);
        StreamResult result = new StreamResult(new File("junit_output.xml"));
        transformer.transform(source, result);

        System.out.println("Gherkin feature converted to JUnit XML: junit_output.xml");
    }
}
