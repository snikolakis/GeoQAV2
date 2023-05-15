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
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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

class Instance {
	public int begin;
	public int end;
	public String uri;
}

class PropertyPEMF {
	public int begin;
	public int end;
	public String uri;
}

@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties
 * (spring.boot.admin.url)
 * 
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F"
 *      target="_top">Github wiki howto</a>
 */
public class SubgraphIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(SubgraphIdentifier.class);
	private static final Map<String, String> class_names_mapping = new HashMap<String, String>();
	private Map<String, ArrayList<String>> synonymsList = new HashMap<String, ArrayList<String>>();

	private static Map<String, String> loadListOfInstances(String fname) {
		Map<String, String> instanceClasses = new HashMap<String, String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line = "";
			while ((line = br.readLine()) != null) {
				String splittedLine[] = line.split(";");
				instanceClasses.put(splittedLine[1].trim(), splittedLine[0].trim());
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			return instanceClasses;
		}
	}

	private static Map<String, ArrayList<String>> loadListOfSynonyms(String fname) {
		Map<String, ArrayList<String>> synonyms = new HashMap<String, ArrayList<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line = "";
			while ((line = br.readLine()) != null) {
				String splittedLine[] = line.split(",");
				ArrayList<String> allSynonymsForWord;
				try {
					allSynonymsForWord = new ArrayList<String>(Arrays.asList(splittedLine[1].trim().split("\\+")));
				} catch (Exception e) {
					allSynonymsForWord = new ArrayList<String>();
					allSynonymsForWord.add(splittedLine[1]);
				}
				synonyms.put(splittedLine[0].trim(), allSynonymsForWord);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			return synonyms;
		}
	}

	private static Map<String, ArrayList<String>> loadListOfSuperclassRelations(String fname) {
		Map<String, ArrayList<String>> superclassRelations = new HashMap<String, ArrayList<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line = "";
			while ((line = br.readLine()) != null) {
				String splittedLine[] = line.split(",");
				String class_ = splittedLine[0].trim();
				String superclass = splittedLine[1].trim();
				ArrayList<String> superclasses = superclassRelations.get(class_);
				if (superclasses == null) {
					superclasses = new ArrayList<String>();
				}
				superclasses.add(superclass);
				superclassRelations.put(class_, superclasses);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			return superclassRelations;
		}
	}

	private static ArrayList<String> getAllRelevantClasses(String classLabel, ArrayList<String> superclasses) {
		ArrayList<String> temp = new ArrayList<String>();
		temp.add(classLabel);
		if (superclasses == null) {
			superclasses = new ArrayList<String>();
		}
		temp.addAll(superclasses);
		return temp;
	}

	private static String mapClassName(String original_class_name) {
		String value = class_names_mapping.get(original_class_name);
		if (value != null) {
			return value;
		} else {
			return original_class_name;
		}

	}

	private ArrayList<Subgraph> getSubgraphsBasedOnSynonyms(String question, Set<Concept> concepts,
			ArrayList<Subgraph> possible_subgraphs) {
		// find all subgraphs that satisfy synonyms and then use those as possible
		// subgraphs for the rest of the process
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		String lowercase_question = question.toLowerCase();
		for (String word : this.synonymsList.keySet()) {
			int word_index = lowercase_question.indexOf(word);
			if (word_index != -1) {
				boolean should_ignore_synonym = false;
				for (Concept concept : concepts) {
					// check that this word was not used for any concept found
					if (word_index >= concept.begin && word_index < concept.end) {
						should_ignore_synonym = true;
						break;
					}
				}
				if (!should_ignore_synonym) {
					for (Subgraph subgraph : possible_subgraphs) {
						for (String synonym : this.synonymsList.get(word)) {
							if (subgraph.text.contains(synonym)) {
								temp.add(subgraph);
							}
						}
					}
				}

			}
		}
		if (temp.size() > 0) {
			return temp;
		} else {
			return possible_subgraphs;
		}
	}

	private ArrayList<Subgraph> getSubgraphsBasedOnConcepts(Set<String> conceptsUri,
			Map<String, ArrayList<String>> superclassRelations, ArrayList<Subgraph> possible_subgraphs) {
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		if (conceptsUri.size() == 0) {
			return possible_subgraphs;
		}
		for (String concept : conceptsUri) {
			temp.clear();
			String classLabel = concept.substring(concept.lastIndexOf("#") + 1);
			ArrayList<String> superclasses = superclassRelations.get(classLabel);
			for (String temp_class : getAllRelevantClasses(classLabel, superclasses)) {
				temp_class = mapClassName(temp_class);
				String superclass_text = "";
				if (!temp_class.equals(classLabel)) {
					superclass_text = " (superclass)";
				}
				logger.info("Found concept" + superclass_text + ": " + temp_class);
				for (Subgraph sub : possible_subgraphs) {
					if (sub.entities.contains(temp_class)) {
						temp.add(sub);
					}
				}
			}
		}
		return temp;
	}

	private ArrayList<Subgraph> getSubgraphsBasedOnInstances(Set<String> instancesUri,
			Map<String, String> instanceClasses, Map<String, ArrayList<String>> superclassRelations,
			ArrayList<Subgraph> possible_subgraphs) {
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		if (instancesUri.size() == 0) {
			return possible_subgraphs;
		}
		for (String instance : instancesUri) {
			temp.clear();
			String instanceClassLabel = instanceClasses.get(instance);
			ArrayList<String> superclasses = superclassRelations.get(instanceClassLabel);
			for (String temp_class : getAllRelevantClasses(instanceClassLabel, superclasses)) {
				temp_class = mapClassName(temp_class);
				String superclass_text = "";
				if (!temp_class.equals(instanceClassLabel)) {
					superclass_text = " (superclass)";
				}
				logger.info("Found the instance class" + superclass_text + ": " + temp_class);
				for (Subgraph sub : possible_subgraphs) {
					if (sub.entities.contains(temp_class)) {
						temp.add(sub);
					}
				}
			}
		}
		return temp;
	}

	private ArrayList<Subgraph> getSubgraphsBasedOnProperties(Set<String> propertiesUri,
			ArrayList<Subgraph> possible_subgraphs) {
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		if (propertiesUri.size() == 0) {
			return possible_subgraphs;
		}
		for (String property : propertiesUri) {
			temp.clear();
			String propertyLabel = property.substring(property.lastIndexOf("#") + 1);
			logger.info("Found the property: " + propertyLabel);
			for (Subgraph sub : possible_subgraphs) {
				if (sub.properties.contains(propertyLabel)) {
					temp.add(sub);
				}
			}
		}
		return temp;
	}

	private ArrayList<Subgraph> getSubgraphsBasedOnRules(String question, ArrayList<Subgraph> possible_subgraphs) {
		// add custom logic per question elements
		ArrayList<Subgraph> temp = new ArrayList<Subgraph>();
		boolean foundRule = false;
		String lowercase_question = question.toLowerCase();
		if (lowercase_question.contains("''") || lowercase_question.contains("\"")
				|| lowercase_question.contains("second") || lowercase_question.contains(" sec ")
				|| lowercase_question.contains("when")) {
			foundRule = true;
			temp.clear();
			for (Subgraph sub : possible_subgraphs) {
				// if the question is about time the subgraph should contain
				// - Mode if the question refers to Sensors
				// - Observation otherwise
				if ((sub.entities.contains("Sensor") && sub.entities.contains("Mode"))
						|| sub.entities.contains("Observation")) {
					temp.add(sub);
				}
			}
		}
		if (foundRule) {
			return temp;
		} else {
			return possible_subgraphs;
		}
	}

	private static String getSmallestValidSubgraph(ArrayList<Subgraph> possible_subgraphs) {
		int smallestSubgraphLength = Integer.MAX_VALUE;
		String smallestSubgraph = "";
		for (Subgraph subgraph : possible_subgraphs) {
			int subgraphLength = subgraph.text.split(";").length;
			if (subgraphLength < smallestSubgraphLength) {
				smallestSubgraphLength = subgraphLength;
				smallestSubgraph = subgraph.text;
			}
		}
		return smallestSubgraph;
	}

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

		// initialize mapping between class names
		// class_names_mapping.put("System", "FuelCellSystem");

		// STEP 1: get the required data from the Qanary triplestore (the global process
		// memory)

		// if required, then fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<String>(myQanaryMessage);
		String question = myQanaryQuestion.getTextualRepresentation();

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

		Set<Instance> instances = new HashSet<Instance>();
		Set<String> instancesUri = new HashSet<String>();

		sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
				+ "SELECT ?start ?end ?uri " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
				+ "WHERE { " //
				+ "    ?a a qa:AnnotationOfInstance . " + "?a oa:hasTarget [ "
				+ "		     a               oa:SpecificResource; " //
				+ "		     oa:hasSource    ?q; " //
				+ "	         oa:hasSelector  [ " //
				+ "			         a        oa:TextPositionSelector ; " //
				+ "			         oa:start ?start ; " //
				+ "			         oa:end   ?end " //
				+ "		     ] " //
				+ "    ] . " //
				+ " ?a oa:hasBody ?uri ; "
				+ "    oa:annotatedBy ?annotator " //
				+ "} "
				+ "ORDER BY ?start";

		r = myQanaryUtils.selectFromTripleStore(sparql);
		while (r.hasNext()) {
			QuerySolution s = r.next();
			Instance instanceTemp = new Instance();
			instanceTemp.begin = s.getLiteral("start").getInt();
			instanceTemp.end = s.getLiteral("end").getInt();
			instanceTemp.uri = s.getResource("uri").getURI();
			instances.add(instanceTemp);
			instancesUri.add(instanceTemp.uri);
			// TODO: maybe should be removed
			// dbpediaClassList.add(conceptTemp.link);
			// allClassesList.add(conceptTemp.link);
		}

		Set<PropertyPEMF> propertiesPEMF = new HashSet<PropertyPEMF>();
		Set<String> propertiesUri = new HashSet<String>();

		sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
				+ "SELECT  ?uri ?start ?end "
				+ "FROM <" + myQanaryQuestion.getInGraph() + "> " //
				+ "WHERE { " //
				+ "  ?a a qa:AnnotationOfRelation . " + "  ?a oa:hasTarget [ " + " a    oa:SpecificResource; "
				+ "           oa:hasSource    ?q; " + "				oa:hasSelector  [ " //
				+ "			         a        oa:TextPositionSelector ; " //
				+ "			         oa:start ?start ; " //
				+ "			         oa:end   ?end " //
				+ "		     ] " //
				+ "  ]; " + "     oa:hasValue ?uri ;oa:AnnotatedAt ?time} order by(?time)";

		r = myQanaryUtils.selectFromTripleStore(sparql);
		while (r.hasNext()) {
			QuerySolution s = r.next();
			PropertyPEMF propertyTemp = new PropertyPEMF();
			propertyTemp.begin = s.getLiteral("start").getInt();
			propertyTemp.end = s.getLiteral("end").getInt();
			propertyTemp.uri = s.getResource("uri").getURI();
			propertiesPEMF.add(propertyTemp);
			propertiesUri.add(propertyTemp.uri);
		}

		// STEP 2: compute new knowledge about the given question
		// read superclass relations that may be used as synonyms
		Map<String, ArrayList<String>> superclassRelations = loadListOfSuperclassRelations(
				"qanary_component-SubgraphIdentifier/src/main/resources/superclass_relations.txt");
		this.synonymsList = loadListOfSynonyms(
				"qanary_component-SubgraphIdentifier/src/main/resources/synonyms_list.txt");

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
			logger.error("Missing subgraphs file! '" + file.getName() + "' file is required but no "
					+ file.getAbsolutePath() + " file was found.");
		}

		ArrayList<Subgraph> possible_subgraphs = new ArrayList<Subgraph>(subgraphs);
		Map<String, String> instanceClasses = loadListOfInstances(
				"qanary_component-InstanceIdentifier/src/main/resources/instances.txt");

		possible_subgraphs = getSubgraphsBasedOnSynonyms(question, concepts, possible_subgraphs);

		possible_subgraphs = getSubgraphsBasedOnConcepts(conceptsUri, superclassRelations, possible_subgraphs);
		// if (possible_subgraphs.size() > 0) {
		// String res_subgraphs = "";
		// for (Subgraph sub : possible_subgraphs) {
		// res_subgraphs += sub.text + "\n";
		// }
		// logger.info("Found the following possible subgraphs:\n\n" + res_subgraphs);
		// } else {
		// logger.error("No valid subgraphs found to answer the given question!");
		// }
		possible_subgraphs = getSubgraphsBasedOnProperties(propertiesUri, possible_subgraphs);

		// if (possible_subgraphs.size() > 0) {
		possible_subgraphs = getSubgraphsBasedOnInstances(instancesUri, instanceClasses, superclassRelations,
				possible_subgraphs);
		// }

		possible_subgraphs = getSubgraphsBasedOnRules(question, possible_subgraphs);

		// TODO: remove
		FileWriter fw = new FileWriter("qanary_component-SubgraphIdentifier/src/main/resources/question_sub.csv", true);
		BufferedWriter bw = new BufferedWriter(fw);
		String sensorNameSubgraph = "Sensor;hasName;string_value";
		String sensorReliabilitySubgraph = "Sensor;hasReliability;SensorReliability;atTime_inSec;float_value";
		if (possible_subgraphs.size() > 0) {
			String res_subgraphs = "";
			// for (Subgraph sub : possible_subgraphs) {
			// res_subgraphs += sub.text + "\n";
			// }
			// logger.info("Found the following possible subgraphs:\n\n" + res_subgraphs);
			String smallestValidSubgraph = getSmallestValidSubgraph(possible_subgraphs);
			logger.info("Found smallest valid subgraph: " + smallestValidSubgraph);
			res_subgraphs = smallestValidSubgraph;
			// also, add the Sensor name subgraph if the word "Sensor" is found inside the
			// subgraph
			if (smallestValidSubgraph.contains("Sensor")) {
				res_subgraphs += "+" + sensorNameSubgraph;
			}
			if (smallestValidSubgraph.contains("SensorReliability")) {
				res_subgraphs += "+" + sensorReliabilitySubgraph;
			}
			// TODO: remove
			bw.write("\"" + question + "\",\"" + res_subgraphs + "\"");
		} else {
			logger.error("No valid subgraphs found to answer the given question!");
			// TODO: remove
			bw.write("\"" + question + "\",");
		}
		// TODO; remove
		bw.newLine();
		bw.close();

		// STEP 3: store computed knowledge about the given question into the Qanary
		// triplestore (the global process memory)

		logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
				myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
				myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		// push data to the Qanary triplestore
		// String sparqlUpdateQuery = "..."; // define your SPARQL UPDATE query here
		// myQanaryUtils.updateTripleStore(sparqlUpdateQuery,
		// myQanaryMessage.getEndpoint());

		return myQanaryMessage;
	}
}
