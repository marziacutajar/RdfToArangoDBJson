# RDF to ArangoDB JSON Transformer

This project provides a way of parsing RDF data into a JSON format suitable for the multimodel database ArangoDB.

## How is the RDF transformed?

There are two approaches for transforming the RDF data. 

One approach is to transform each RDF triple into an ArangoDB document such that 
the subject, predicate, and object are each tranformed into a JSON object, and 
these three JSON objects are nested within the ArangoDB document. 
This is called the Basic Approach, as it uses the document model of ArangoDB 
but not the graph model.

The second approach is to transform each unique subject and object into an ArangoDB document of their own.
Predicates are then represented as graph edges between subject and object documents, such that the predicate IRI is stored as an attribute of the edge document. This is called the Graph Approach.

## Running the program

The command-line program expects two input parameters as following:
- -f <file_directory> : Path to the RDF data file
- -m <data_model> : The approach for transforming the RDF data, i.e. a value of 'D' to use the Basic Approach, 'G' to use the Graph Approach

To run the program easily without an IDE, you need to make sure to have the Gradle build tool installed. The program has been built and run succesfully with Gradle version 6.0.1, thus newer backward-compatible versions should also work. Refer to https://gradle.org/install/ for download and installation details.

The program can then be built and run on any file containing valid RDF data to be transformed. A file containing sample RDF data is given in the `sample_data` folder of this repository for use. Simply open your command-line and run the below command, replacing the file path and data model arguments as required.
    
    gradle run --args="-f=sample_data\bsbm_dataset_1000products.ttl -m=D"
    
Please note that this command, as well as all others given below, work on a Windows OS. Please modify accordingly if using any other operating system.

Another option is to create a fat JAR file using Gradle by executing the below in command-line:
   
    gradle fatJar
    
The jar file will be saved to the build\libs directory within the main project directory. To run the jar file, navigate 
to the directory containing the file and run as follows:
    
    cd build\libs
    
    java -jar RDF_to_Arango_Transformer.jar -f <path_to_rdf_data_file_here> -m D

## Program outputs

If the Basic Approach is used, the program will output one file containing the created JSON documents.
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

For the Basic Approach, the data in the produced file can be imported using a command such as the below:

    arangoimport --file arango_documents_201912150913.json --collection triples --create-collection true 
    --batch-size 1000000000 --server.database database_name

The user should modify the command-line arguments accordingly. 

For the Graph Approach, the two files of data containing resources and literals must be imported first using two commands such as the below:
    
    arangoimport --file arango_resources_201912150936.json --collection vertices_resources --create-collection true 
    --batch-size 1000000000 --server.database database_name

    arangoimport --file arango_literals_201912150936.json --collection vertices_literals --create-collection true 
    --batch-size 1000000000 --server.database database_name

Using the above commands, we import the resource documents into a collection called vertices_resources, and the literal documents into a seperate collection called vertices_literals. Two different collections are used to store vertices and literals for more optimal performance when querying the data. However, these can be stored in the same collection if desired, simply by specifying the same collection name in both commands.

The other two files containing edge documents can then be imported. Please make sure that you have created the required edge collection(s) in your
database before importing. We use the below commands to import the data:
    
    arangoimport --file arango_edges_resources_201912150936.json --collection edges --from-collection-prefix vertices_resources 
    --to-collection-prefix vertices_resources --batch-size 1000000000 --server.database database_name

    arangoimport --file arango_edges_literals_201912150936.json --collection edges --from-collection-prefix vertices_resources 
    --to-collection-prefix vertices_literals --batch-size 1000000000 --server.database database_name



