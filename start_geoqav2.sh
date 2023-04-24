#!/bin/bash
screen -dmS qanary-pipeline -L java -jar qanary_pipeline-template/target/qa.pipeline-2.4.0.jar
#screen -dmS qanary-tagmedisambiguate java -jar qanary_component-TagMeDisambiguateYago/target/qanary_component-TagMeDisambiguate-0.1.0.jar
screen -dmS qanary-concept -L java -jar qanary_component-ConceptIdentifierYago/target/qanary_component-ConceptIdentifier-0.1.0.jar
# screen -dmS qanary-relation -L java -jar qanary_component-RelationDetection/target/qanary_component-RelationDetection-0.1.0.jar
screen -dmS qanary-property -L java -jar qanary_component-PropertyIdentifierPEMF/target/qanary_component-PropertyIdentifierPEMF-0.0.1.jar
#screen -dmS qanary-geosparqlgenerator -L java -jar qanary_component-GeoSparqlGeneratorYago/target/qanary_component-GeoSparqlGenerator-0.1.0.jar
screen -dmS qanary-instance -L java -jar qanary_component-InstanceIdentifier/target/qanary_component-InstanceIdentifier-0.0.1.jar
screen -dmS qanary-subgraph -L java -jar qanary_component-SubgraphIdentifier/target/qanary_component-SubgraphIdentifier-0.0.1.jar
