# RDF to ArangoDB JSON Transformer

This project provides a way of parsing RDF data into a JSON format suitable for the multimodel database ArangoDB.

## How is the RDF transformed?

There are two approaches for transforming the RDF data. 

One approach is to transform each RDF triple into an ArangoDB document such that 
the subject, predicate, and object are each tranformed into a JSON object, and 
these three JSON objects are nested within the ArangoDB document. 
This is called the Document approach, as it uses the document model of ArangoDB 
but not the graph model.
#Each JSON object has a type attribute that specifies whether it is a URI resource, a blank node, or a literal. 

The second approach is to transform each unique subject and object into an ArangoDB document of their own.
Predicates are then represented as graph edges between subject and object documents, 
such that the predicate IRI is stored as an attribute of the edge document.

## Running the program

To run the program easily without an IDE, you need to make sure to have the Gradle build tool installed. 
Refer to https://gradle.org/install/ for download and installation details.

The program can then be built and run with a single command as below:
    gradle run --args="-f=C:\Users\marzia\Documents\test_data\test.ttl -m=D"

The command-line program expects two input parameters as following:
-f: Path to the RDF data file
-m: The approach for transforming the RDF data, i.e. a value of D to use the Document Approach, G to use the Graph Approach

java -jar RDF-to-Arango-Transformer.jar -f "C:\Users\marzia\Documents\Test Data\testdata.ttl" -m D

## Program outputs

If the Document Approach is used, the program will output one file containing the created JSON documents.
This file can be found in the /results directory within the project folder. The file name is in the format
arango_documents_<current_datetime>.json 

If the Graph Approach is used, the program will output four files. These can be found in the /results directory within the project folder.
These files contain the below data:
1. arango_resources_<current_datetime>.json - contains all the ArangoDB documents that represent IRIs or blank nodes.
2. arango_literals_<current_datetime>.json - contains all the ArangoDB documents that represent literal values.
3. arango_edges_resources_<current_datetime>.json - contains all the edge documents for predicates linking a subject to an IRI or blank node object.
4. arango_edges_literals_<current_datetime>.json - contains all the edge documents for predicates linking a subject to a literal object.

## Importing the data into ArangoDB

The JSON data can be imported into ArangoDB using the [Arangoimport](https://www.arangodb.com/docs/stable/programs-arangoimport-examples-json.html) command-line tool.

For the Document Approach, the data in the produced file can be imported using a command such as the below:
arangoimport --file arango_documents_201912150913.json --collection triples --create-collection true 
--batch-size 1000000000 --server.database database_name

The user should modify the command-line arguments accordingly. 

For the Graph Approach, the two files of data containing resources and literals must be imported first using two commands such as the below:
arangoimport --file arango_resources_201912150936.json --collection vertices_resources --create-collection true 
--batch-size 1000000000 --server.database database_name
arangoimport --file arango_literals_201912150936.json --collection vertices_literals --create-collection true 
--batch-size 1000000000 --server.database database_name

Using the above commands, we import the resource documents into a collection called vertices_resources, and the literal documents into a seperate
collection called vertices_literals. However, these can be stored in the same collection if desired, simply by specifying the same collection name
in both commands.

The other two files containing edge documents can then be imported. Please make sure that you have created the required edge collection(s) in your
database before importing. We use the below commands to import the data:
arangoimport --file arango_edges_resources_201912150936.json --collection edges --from-collection-prefix vertices_resources 
--to-collection-prefix vertices_resources --batch-size 1000000000 --server.database database_name
arangoimport --file arango_edges_literals_201912150936.json --collection edges --from-collection-prefix vertices_resources 
--to-collection-prefix vertices_literals --batch-size 1000000000 --server.database database_name



