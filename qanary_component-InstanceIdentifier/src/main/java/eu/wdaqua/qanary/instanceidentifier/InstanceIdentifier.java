package eu.wdaqua.qanary.instanceidentifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.debatty.java.stringsimilarity.JaroWinkler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@Component
/**
 * This component connected automatically to the Qanary pipeline. The Qanary
 * pipeline endpoint defined in application.properties (spring.boot.admin.url)
 *
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F"
 *      target="_top">Github wiki howto</a>
 */
public class InstanceIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(InstanceIdentifier.class);

	static List<String> allInstanceWordUri = new ArrayList<String>();
	static List<String> osmClass = new ArrayList<String>();
	static List<String> commonClasses = new ArrayList<String>();
	static List<String> stopwords = new ArrayList<String>();
	static Map<String, String> osmUriMap = new HashMap<String, String>();
	static Map<String, String> DBpediaUrimap = new HashMap<String, String>();
	static Map<String, String> instances = new HashMap<>();
	public static void getCommonClass(Set<String> dbpediaInstances) {

		for (String lab : osmClass) {

			if (dbpediaInstances.contains(lab)) {
				if (!commonClasses.contains(lab))
					commonClasses.add(lab);
			}
		}

	}

	public static void loadListOfInstances(String fname){
		try{
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line = "";
			while((line = br.readLine())!=null){
				String splittedLine[] = line.split(";");
//				System.out.println("0: "+splittedLine[0]+"\t 1:"+splittedLine[1]);
				instances.put(splittedLine[1].trim(),splittedLine[0].trim());
			}
			br.close();
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	public static void loadListOfStopWords(String fname){
		try{
			BufferedReader br = new BufferedReader(new FileReader(fname));
			String line = "";
			while((line = br.readLine())!=null){
				stopwords.add(line.trim());
			}
			br.close();
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	public static void getXML(String fname) {
		try {
			File fXmlFile = new File(fname);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("owl:Class");
			for (int temp = 0; temp < nList.getLength(); temp++) {

				Node nNode = nList.item(temp);
				String uri, cEntity;

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;
					uri = eElement.getAttribute("rdf:about");
					osmUriMap.put(uri.substring(uri.indexOf('#') + 1), uri);
					uri = uri.substring(uri.indexOf('#') + 1);
					osmClass.add(uri);
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static List<String> getNouns(String documentText) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos,lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		List<String> postags = new ArrayList<>();
		String lemmetizedQuestion = "";
		// Create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);
		// run all Annotators on this text
		pipeline.annotate(document);
		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				String pos = token.get(PartOfSpeechAnnotation.class);
				if (pos.contains("NN")) {
					postags.add(token.get(LemmaAnnotation.class));
				}
			}
		}
		return postags;
	}

	public static ArrayList<String> ngrams(int n, String str) {
		ArrayList<String> ngrams = new ArrayList<String>();
		String[] words = str.split(" ");
		for (int i = 0; i < words.length - n + 1; i++)
			ngrams.add(concat(words, i, i+n));
		return ngrams;
	}
	public static String concat(String[] words, int start, int end) {
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < end; i++)
			sb.append((i > start ? " " : "") + words[i]);
		return sb.toString();
	}

	public static String lemmatize(String documentText) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		List<String> lemmas = new ArrayList<>();
		String lemmetizedQuestion = "";
		// Create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);
		// run all Annotators on this text
		pipeline.annotate(document);
		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				lemmas.add(token.get(LemmaAnnotation.class));
				lemmetizedQuestion += token.get(LemmaAnnotation.class) + " ";
			}
		}
		return lemmetizedQuestion;
	}

	static String remove_stopwords(String question) {
		String[] words = question.split(" ");
		List<String> result = new ArrayList<String>();
		for (String word: words) {
			if (!stopwords.contains(word)) {
				result.add(word);
			}
		}
		return String.join(" ", result);
	}

	static int wordcount(String string)
	{
		int count=0;

		char ch[]= new char[string.length()];
		for(int i=0;i<string.length();i++)
		{
			ch[i]= string.charAt(i);
			if( ((i>0)&&(!Character.isUpperCase(ch[i]))&&(Character.isUpperCase(ch[i-1]))) || ((i>0) && (ch[i] != '_')&&(ch[i-1] == '_')) || ((ch[0]!=' ')&&(i==0)) )
				count++;
		}
		return count;
	}

	static List<Instance> removeOverlappingNgramInstances(List<Instance> mappedInstances)
	{
		List<Instance> result = new ArrayList<Instance>();
		for (Instance instance:mappedInstances) {
			for (Instance instance2:mappedInstances) {
				if (!instance.getLabel().equals(instance2.getLabel()) || mappedInstances.size() == 1) {
					if (instance.getBegin() <= instance2.getBegin() && instance.getEnd() >= instance2.getEnd()) {
						result.add(instance);
					}
				}
			}
		}
		return result;
	}

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 *
	 * @throws Exception
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		// Map<String, String> allMapInstanceWord =
		// DBpediaInstancesAndURIs.getDBpediaInstancesAndURIs();
//		Map<String, ArrayList<String>> allMapInstanceWord = YagoInstancesAndURIs.getYagoInstancesAndURIs();
//		getXML("qanary_component-InstanceIdentifier/src/main/resources/osm.owl");

		// getCommonClass(allMapInstanceWord.keySet());
		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
		List<Instance> mappedInstances = new ArrayList<Instance>();
		// List<Instance> DBpediaInstances = new ArrayList<Instance>();
		// List<Instance> osmInstances = new ArrayList<Instance>();
		// List<Instance> yago2geoInstances = new ArrayList<>();
		List<String> allNouns = getNouns(myQanaryQuestion.getTextualRepresentation());
		loadListOfInstances("qanary_component-InstanceIdentifier/src/main/resources/instances.txt");
		loadListOfStopWords("qanary_component-InstanceIdentifier/src/main/resources/stop_words_english.txt");
		// question string is required as input for the service call
//		osmUriMap.put("District", "http://www.app-lab.eu/gadm/District");
//		osmUriMap.put("County", "http://www.app-lab.eu/gadm/County");
//		osmUriMap.put("Administrative County", "http://www.app-lab.eu/gadm/AdministrativeCounty");
//		osmUriMap.put("London Borough", "http://www.app-lab.eu/gadm/LondonBorough");
//		osmUriMap.put("MetropolitanCounty", "http://www.app-lab.eu/gadm/MetropolitanCounty");
//		osmUriMap.put("Country", "http://www.app-lab.eu/gadm/HomeNation|ConstituentCountry");
//		osmUriMap.put("Province", "http://www.app-lab.eu/gadm/Province");
//		osmUriMap.put("Unitary District", "http://www.app-lab.eu/gadm/UnitaryDistrict");
//		osmUriMap.put("Administrative Unit", "http://www.app-lab.eu/gadm/AdministrativeUnit");
//		osmUriMap.put("Metropolitan Borough", "http://www.app-lab.eu/gadm/MetropolitanBorough");
		/*osmUriMap.put("Site", "http://www.app-lab.eu/osm/Attraction");
//		allMapInstanceWord.put("point","http://yago-knowledge.org/resource/wordnet_mountain_109359803");
		// -----------------------------------------------------------------------------------------
		osmUriMap.put("Civil Parishor Community", "http://kr.di.uoa.gr/yago2geo/ontology/OS_CivilParishorCommunity");
		osmUriMap.put("Unitary Authority Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_UnitaryAuthorityWard");
		osmUriMap.put("District Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_DistrictWard");
		osmUriMap.put("District", "http://kr.di.uoa.gr/yago2geo/ontology/OS_District");
		osmUriMap.put("County", "http://kr.di.uoa.gr/yago2geo/ontology/OS_County");
		osmUriMap.put("Metropolitan District Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_MetropolitanDistrictWard");
		osmUriMap.put("Unitary Authority", "http://kr.di.uoa.gr/yago2geo/ontology/OS_UnitaryAuthority");
		osmUriMap.put("London Borough", "http://kr.di.uoa.gr/yago2geo/ontology/OS_LondonBorough");
		osmUriMap.put("London Borough Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_LondonBoroughWard");
		osmUriMap.put("Metropolitan District", "http://kr.di.uoa.gr/yago2geo/ontology/OS_MetropolitanDistrict");
		osmUriMap.put("GreaterLondon Authority", "http://kr.di.uoa.gr/yago2geo/ontology/OS_GreaterLondonAuthority");
		osmUriMap.put("European Region", "http://kr.di.uoa.gr/yago2geo/ontology/OS_EuropeanRegion");
		osmUriMap.put("Community Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_COMMUNITYWARD");
		osmUriMap.put("City Community Ward", "http://kr.di.uoa.gr/yago2geo/ontology/OS_CCOMMUNITYWARD");*/


		/*BufferedReader br = new BufferedReader(new FileReader("qanary_component-InstanceIdentifier/src/main/resources/yago2geoOsmclasses.txt"));
		String line = "";
		while((line = br.readLine())!=null){
			String splitted[] = line.split(",");
			if(osmUriMap.containsKey(splitted[1])) {
				System.out.println("updated class: "+ osmUriMap.get(splitted[1])+" to : "+splitted[1]);
				osmClass.remove(osmUriMap.get(splitted[1]));
				osmUriMap.remove(splitted[1]);
			}
			osmUriMap.put(splitted[1],splitted[0]);
			osmClass.add(splitted[0]);
		}
		br.close();*/

		//osmUriMap.remove("county");
		/*
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_CivilParishorCommunity
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_UnitaryAuthorityWard
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_DistrictWard
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_District
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_County
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_MetropolitanDistrictWard
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_UnitaryAuthority
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_LondonBorough
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_LondonBoroughWard
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_MetropolitanDistrict
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_GreaterLondonAuthority
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_EuropeanRegion
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_COMMUNITYWARD
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_COMMUNITY
		 * http://kr.di.uoa.gr/yago2geo/ontology/OS_CCOMMUNITY
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_DecentralizedAdministration
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_Municipality
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_MunicipalUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_Region
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_RegionalUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GAG_MunicipalCommunity
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_1stOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_2ndOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_3rdOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_4thOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_5thOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/GADM_6thOrder_AdministrativeUnit
		 * http://kr.di.uoa.gr/yago2geo/ontology/OSM_forest
http://kr.di.uoa.gr/yago2geo/ontology/OSM_park
http://kr.di.uoa.gr/yago2geo/ontology/OSM_nature_reserve
http://kr.di.uoa.gr/yago2geo/ontology/OSM_lake
http://kr.di.uoa.gr/yago2geo/ontology/OSM_beach
http://kr.di.uoa.gr/yago2geo/ontology/OSM_bay
http://kr.di.uoa.gr/yago2geo/ontology/OSM_reservoir
http://kr.di.uoa.gr/yago2geo/ontology/OSM_lagoon
http://kr.di.uoa.gr/yago2geo/ontology/OSM_oxbow
http://kr.di.uoa.gr/yago2geo/ontology/OSM_village
http://kr.di.uoa.gr/yago2geo/ontology/OSM_locality
http://kr.di.uoa.gr/yago2geo/ontology/OSM_town
http://kr.di.uoa.gr/yago2geo/ontology/OSM_island
http://kr.di.uoa.gr/yago2geo/ontology/OSM_city
http://kr.di.uoa.gr/yago2geo/ontology/OSM_stream
http://kr.di.uoa.gr/yago2geo/ontology/OSM_canal
		 */
		String myQuestion = lemmatize(myQanaryQuestion.getTextualRepresentation());

		String myQuestionNl = myQanaryQuestion.getTextualRepresentation();

		logger.info("Lemmatize Question: {}", myQuestion);
		logger.info("store data in graph {}",
				myQanaryMessage.getValues().get(new URL(myQanaryMessage.getEndpoint().toString())));
		// WordNetAnalyzer wordNet = new WordNetAnalyzer("qanary_component-InstanceIdentifier/src/main/resources/WordNet-3.0/dict");
		// osmUriMap.remove("county");

		/*for (String instanceLabel : allMapInstanceWord.keySet()) {
			// logger.info("The word: {} question : {}", instanceLabel,
			// myQuestion);

			ArrayList<String> wordNetSynonyms = wordNet.getSynonyms(instanceLabel);
			*//*for (String synonym : wordNetSynonyms) {
				for (String nounWord : allNouns) {
					Pattern p = Pattern.compile("\\b" + synonym + "\\b", Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(nounWord);
					if (m.find()) {
						for (String s : allMapInstanceWord.get(instanceLabel.replaceAll(" ", "_"))) {
							Instance instance = new Instance();
							int begin = myQuestion.toLowerCase().indexOf(synonym.toLowerCase());
							instance.setBegin(begin);
							instance.setEnd(begin + synonym.length());
							instance.setURI(s);
							mappedInstances.add(instance);
							System.out.println(
									"Identified Instances: yago:" + instanceLabel + " ============================"
											+ "Synonym inside question is: " + synonym + " ===================");
							logger.info("identified instance: instance={} : {} : {}", instance.toString(), myQuestion,
									instanceLabel);
						}
						// TODO: remove break and collect all appearances of
						// instances
						// TODO: implement test case "City nearby Forest
						// nearby
						// River"
						break;
					}
				}
			}*//*
		}*/

		/*for (String instanceLabel : osmUriMap.keySet()) {
			// logger.info("The word: {} question : {}", instanceLabel,
			// myQuestion);

			ArrayList<String> wordNetSynonyms = wordNet.getSynonyms(instanceLabel);
			for (String synonym : wordNetSynonyms) {
				for (String nounWord : allNouns) {
					Pattern p = Pattern.compile("\\b" + synonym + "\\b", Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(nounWord);
					if (m.find()) {
						Instance instance = new Instance();
						int begin = myQuestion.toLowerCase().indexOf(synonym.toLowerCase());
						instance.setBegin(begin);
						instance.setEnd(begin + synonym.length());
						instance.setURI(osmUriMap.get(instanceLabel.replaceAll(" ", "_")));
						mappedInstances.add(instance);
						System.out.println("Identified Instances: osm:" + instanceLabel + " ============================"
								+ "Synonym inside question is: " + synonym + " ===================");
						logger.info("identified instance: instance={} : {} : {}", instance.toString(), myQuestion,
								instance.getURI());
						// TODO: remove break and collect all appearances of
						// instances
						// TODO: implement test case "City nearby Forest
						// nearby
						// River"
						break;
					}
				}
			}
		}*/
		// the question without stopwords
		String questionWoStopwords = remove_stopwords(myQuestion);
		//sliding window for string similarity
		boolean falgFound = false;
		// String lastInstance = "";
		for (String instanceLabelUri : instances.keySet()) {
			String instanceLabel = instanceLabelUri.substring(instanceLabelUri.lastIndexOf("#") + 1);
			// int wordCount = wordcount(instanceLabel);
			int wordCount = 1;
//			System.out.println("total words :"+wordCount+"\t in : "+instanceLabel);
//			String wordsOdSentence[] = myQuestionNl.split(" ");
			List<String> ngramsOfquestion = ngrams(wordCount,questionWoStopwords);//myQuestion
			JaroWinkler jw = new JaroWinkler();
			double similarityScore = 0.0;
			for(String ngramwords: ngramsOfquestion){
				similarityScore = jw.similarity(ngramwords.toLowerCase(Locale.ROOT),instanceLabel.toLowerCase(Locale.ROOT));
				System.out.println("got similarity for  ngram :"+ngramwords+"\t and instance label : "+instanceLabel+"\t is = "+similarityScore);
				if(similarityScore>0.98){
					System.out.println("====================got similarity more than 98 for  ngram :"+ngramwords+"\t and instance label : "+instanceLabel);
					falgFound = true;
					Instance instance = new Instance();
					String[] ng = ngramwords.split(" ");
					int begin = myQuestion.toLowerCase().indexOf(ng[0].toLowerCase());
					String lastWordOfNgram = ng[ng.length-1];
					int end = myQuestion.toLowerCase().indexOf(lastWordOfNgram.toLowerCase()) + lastWordOfNgram.length();
					instance.setBegin(begin);
					instance.setEnd(end);
					instance.setURI(instanceLabelUri);
					instance.setLabel(instanceLabel);
					mappedInstances.add(instance);
					System.out.println("Identified Instances: " + instanceLabel + " ============================"
							+ "ngram inside question is: " + ngramwords + " ===================");
					logger.info("identified instance: instance={} : {} : {}", instance.toString(), myQuestion,
							instance.getURI());
					break;
				}
			}
			// similarity between two words of the sentence and the graph instances
			// if (!lastInstance.equals("")) {
			// 	for(){
			// 		similarityScore = jw.similarity(ngramwords.toLowerCase(Locale.ROOT),lastInstance.toLowerCase(Locale.ROOT)+" "+instanceLabel.toLowerCase(Locale.ROOT));
			// 		System.out.println("got similarity for  ngram :"+ngramwords+"\t and instance label : "+instanceLabel+"\t is = "+similarityScore);
			// 		if(similarityScore>0.98){
			// 			System.out.println("====================got similarity more than 98 for  ngram :"+ngramwords+"\t and instance label : "+lastInstance+" "+instanceLabel);
			// 			falgFound = true;
			// 			Instance instance = new Instance();
			// 			int begin = myQuestion.toLowerCase().indexOf(ngramwords.toLowerCase());
			// 			instance.setBegin(begin);
			// 			instance.setEnd(begin + ngramwords.length());
			// 			instance.setURI(instances.get(instanceLabel));
			// 			instance.setLabel(instanceLabel);
			// 			mappedInstances.add(instance);
			// 			System.out.println("Identified Instances: " + instanceLabel + " ============================"
			// 					+ "ngram inside question is: " + ngramwords + " ===================");
			// 			logger.info("identified instance: instance={} : {} : {}", instance.toString(), myQuestion,
			// 					instance.getURI());
			// 			break;
			// 		}
			// 	}		
			// }
			/*if(falgFound){
				break;
			}*/
		}
		logger.info("The instances found before filtering are: " + mappedInstances);
		mappedInstances = removeOverlappingNgramInstances(mappedInstances);
		logger.info("The instances found after filtering are: " + mappedInstances);
		// TODO: probably do not need synonyms for our use case
// 		if(!falgFound){
// 			for (String instanceLabel : instances.keySet()) {
// //			System.out.println("============Got Inside==============");
// 				ArrayList<String> wordNetSynonyms = wordNet.getSynonyms(instanceLabel);
// 				for (String synonym : wordNetSynonyms) {
// 					for (String nounWord : allNouns) {
// 						Pattern p = Pattern.compile("\\b" + synonym + "\\b", Pattern.CASE_INSENSITIVE);
// 						Matcher m = p.matcher(nounWord);
// //					System.out.println("for synonym : "+synonym+"\t noun word : "+nounWord);
// 						if (m.find()) {
// 							Instance instance = new Instance();
// 							int begin = myQuestionNl.toLowerCase().indexOf(synonym.toLowerCase());
// 							instance.setBegin(begin);
// 							instance.setEnd(begin + synonym.length());
// 							instance.setURI(instances.get(instanceLabel));
// 							mappedInstances.add(instance);
// 							System.out.println("Identified Instances: yago2geo:" + instanceLabel + " ============================"
// 									+ "Synonym inside question is: " + synonym + " ===================");
// 							logger.info("identified instance: instance={} : {} : {}", instance.toString(), myQuestion,
// 									instance.getURI());
// 							break;
// 						}
// 					}
// 				}
// 			}
// 		}


		// ArrayList<Instance> removalList = new ArrayList<Instance>();

//		for (Instance tempInstance : mappedInstances) {
//			String conUri = tempInstance.getURI();
//			if (conUri != null) {
//				if (conUri.contains("Parking")) {
//					System.out.println("Getting in parking with question : " + myQuestionNl);
//					if (!myQuestionNl.toLowerCase().contains("parking") && !myQuestionNl.contains(" car ")) {
//						System.out.println("getting in car parking :" + myQuestion);
//						removalList.add(tempInstance);
//					}
//				}else if (conUri.contains("Park")) {
//					System.out.println("Getting in park with question : " + myQuestionNl);
//					if (myQuestionNl.toLowerCase().contains(" car park")) {
//						System.out.println("getting in car parking :" + myQuestion);
//						if(tempInstance.getURI().contains("dbpedia"))
//						tempInstance.setURI("http://dbpedia.org/ontology/Parking");
//						if(tempInstance.getURI().contains("app-lab"))
//							tempInstance.setURI("http://www.app-lab.eu/osm/ontology#Parking");
//					}
//				}
//				if(conUri.contains("http://dbpedia.org/ontology/Area")){
//					removalList.add(tempInstance);
//				}
//				if (conUri.contains("Gondola") || conUri.contains("http://dbpedia.org/ontology/List")
//						|| conUri.contains("http://dbpedia.org/ontology/Automobile")
//						|| conUri.contains("http://dbpedia.org/ontology/Altitude")
//						|| conUri.contains("http://dbpedia.org/ontology/Name")
//						|| conUri.contains("http://dbpedia.org/ontology/Population")
//						|| (conUri.contains("http://www.app-lab.eu/osm/ontology#Peak") || (conUri.contains("http://dbpedia.org/ontology/Area"))
//								&& myQuestion.toLowerCase().contains("height"))) {
//					removalList.add(tempInstance);
//				}
//			}
////			System.out.println("Instance: " + conUri);
//		}
//
		// for (Instance removalC : removalList) {
		// 	mappedInstances.remove(removalC);
		// }

		for (Instance mappedInstance : mappedInstances) {
			// insert data in QanaryMessage.outgraph
			logger.info("apply vocabulary alignment on outgraph: {}", myQanaryQuestion.getOutGraph());
			String sparql = "" //
					+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
					+ "INSERT { " //
					+ "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
					+ "  ?a a qa:AnnotationOfInstance . " //
					+ "  ?a oa:hasTarget [ " //
					+ "           a oa:SpecificResource; " //
					+ "             oa:hasSource    ?source; " //
					+ "             oa:hasSelector  [ " //
					+ "                    a oa:TextPositionSelector ; " //
					+ "                    oa:start \"" + mappedInstance.getBegin() + "\"^^xsd:nonNegativeInteger ; " //
					+ "                    oa:end   \"" + mappedInstance.getEnd() + "\"^^xsd:nonNegativeInteger  " //
					+ "             ] " //
					+ "  ] . " //
					+ "  ?a oa:hasBody ?mappedInstanceURI;" //
					+ "     oa:annotatedBy qa:InstanceIdentifier; " //
					+ "}} " //
					+ "WHERE { " //
					+ "  BIND (IRI(str(RAND())) AS ?a) ."//
					+ "  BIND (now() AS ?time) ." //
					+ "  BIND (<" + mappedInstance.getURI() + "> AS ?mappedInstanceURI) ." //
					+ "  BIND (<" + myQanaryQuestion.getUri() + "> AS ?source  ) ." //
					+ "}";
			logger.debug("Sparql query to add instances to Qanary triplestore: {}", sparql);
			myQanaryUtils.updateTripleStore(sparql, myQanaryQuestion.getEndpoint().toString());
		}

		return myQanaryMessage;
	}

}
