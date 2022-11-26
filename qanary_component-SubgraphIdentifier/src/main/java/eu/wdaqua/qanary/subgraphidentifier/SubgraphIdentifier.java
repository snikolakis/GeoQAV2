package eu.wdaqua.qanary.subgraphidentifier;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;

class Subgraph {
	public ArrayList<String> properties;
	public ArrayList<String> entities;
	public String text;
}

class Concept {
	public int begin;
	public int end;
	public String link;
}

@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class SubgraphIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(SubgraphIdentifier.class);

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component, some helping notes w.r.t. the typical 3 steps of implementing a
	 * Qanary component are included in the method (you might remove all of them)
	 * 
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

		
		
		// STEP 1: get the required data from the Qanary triplestore (the global process
		// memory)

		// if required, then fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<String>(myQanaryMessage);

		ResultSet r;
		Set<Concept> concepts = new HashSet<Concept>();
		Set<String> conceptsUri = new HashSet<String>();

		String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
				+ "SELECT ?start ?end ?uri " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
				+ "WHERE { " //
				+ "    ?a a qa:AnnotationOfConcepts . " + "?a oa:hasTarget [ "
				+ "		     a               oa:SpecificResource; " //
				+ "		     oa:hasSource    ?q; " //
				+ "	         oa:hasSelector  [ " //
				+ "			         a        oa:TextPositionSelector ; " //
				+ "			         oa:start ?start ; " //
				+ "			         oa:end   ?end " //
				+ "		     ] " //
				+ "    ] . " //
				+ " ?a oa:hasBody ?uri ; " + "    oa:annotatedBy ?annotator " //
				+ "} " + "ORDER BY ?start ";

		r = myQanaryUtils.selectFromTripleStore(sparql);
		while (r.hasNext()) {
			QuerySolution s = r.next();
			Concept conceptTemp = new Concept();
			conceptTemp.begin = s.getLiteral("start").getInt();
			conceptTemp.end = s.getLiteral("end").getInt();
			conceptTemp.link = s.getResource("uri").getURI();
			concepts.add(conceptTemp);
			conceptsUri.add(conceptTemp.link);
			// TODO: maybe should be removed
			// dbpediaClassList.add(conceptTemp.link);
			// allClassesList.add(conceptTemp.link);
		}

		
		
		// STEP 2: compute new knowledge about the given question
		// read subgraphs to identify against the identified concepts
		File file = new File("qanary_component-SubgraphIdentifier/src/main/resources/subgraphs_all.txt");
		ArrayList<Subgraph> subgraphs = new ArrayList<Subgraph>();
		if (file.exists()) {
			logger.info("Reading file " + file.getAbsolutePath());
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = "";
			while ((line = br.readLine()) != null) {
				String splitedLine[] = line.split(";");
				Subgraph subgraph = new Subgraph();
				ArrayList<String> properties = new ArrayList<String>();
				ArrayList<String> entities = new ArrayList<String>();
				int i = 0;
				while (i < splitedLine.length) {
					if (i % 2 == 0) {
						entities.add(splitedLine[i]);
					} else {
						properties.add(splitedLine[i]);
					}
					i++;
				}
				subgraph.properties = properties;
				subgraph.entities = entities;
				subgraph.text = line;
				subgraphs.add(subgraph);
			}
			br.close();
		} else {
			logger.error("Missing subgraphs file! '" + file.getName() + "' file is required but no " + file.getAbsolutePath() + " file was found.");
		}

		ArrayList<Subgraph> possible_subgraphs = new ArrayList<Subgraph>(subgraphs);
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		for (String concept : conceptsUri) {
			String classLabel = concept.substring(concept.lastIndexOf("#") + 1);
			temp.clear();
			for (Subgraph sub : possible_subgraphs) {
				if (sub.entities.contains(classLabel)) {
					temp.add(sub);
				}
			}
			possible_subgraphs.clear();
			possible_subgraphs.addAll(temp);
		}

		if (possible_subgraphs.size() > 0) {
			String res_subgraphs = "";
			for (Subgraph sub : possible_subgraphs) {
				res_subgraphs += sub.text + "\n";
			}
			logger.info("Found the following possible subgraphs:\n\n" + res_subgraphs);
		} else {
			logger.error("No valid subgraphs found to answer the given question!");
		}
		
		// STEP 3: store computed knowledge about the given question into the Qanary
		// triplestore (the global process memory)

		logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
				myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
				myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		// push data to the Qanary triplestore
		// String sparqlUpdateQuery = "..."; // define your SPARQL UPDATE query here
		// myQanaryUtils.updateTripleStore(sparqlUpdateQuery, myQanaryMessage.getEndpoint());

		return myQanaryMessage;
	}
}
