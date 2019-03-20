package com.rdfarango;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.commons.cli.*;
import org.apache.jena.rdf.model.*;
import com.rdfarango.constants.ArangoAttributes;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.SplitIRI;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO add comments for every important part

public class Main {

    private static int blank_node_count = 0;
    private static int namespace_count = 0;

    private static Map<String, Integer> URI_RESOURCES_MAP = new HashMap<>();
    private static Map<String, String> LITERALS_MAP = new HashMap<>();
    private static Map<String, String> BLANK_NODES_MAP = new HashMap<>();
    private static Map<String, String> PROPERTIES_MAP = new HashMap<>();
    //TODO properties can't also be used as subjects/objects, so if this is encountered in data, return error to user

    public static void main(String[] args) {
        // create the parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        //options.addOption( "f", "all", true, "Path to rdf file");
        options.addOption(Option.builder("f").longOpt("file").hasArg().desc("Path to rdf file").argName("file").required().build());

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            Model model = ModelFactory.createDefaultModel() ;

            System.out.println("Reading RDF file...");
            model.read(line.getOptionValue("f")) ;

            System.out.println("Parsing RDF into JSON...");
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode jsonValues = RDFObjectsToJson(mapper, model);
            //ArrayNode jsonEdges = RDFTriplesToJson(mapper, model);

            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File("arango_values.json"), jsonValues);
            //writer.writeValue(new File("arango_edges.json"), jsonEdges);
        }
        catch(ParseException exp) {
            // oops, something went wrong
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

    private static ArrayNode RDFObjectsToJson(ObjectMapper mapper, Model model){
        ArrayNode json_documents = mapper.createArrayNode();

        //iterate over all namespaces and create json doc for each
        Map<String, String> nsPrefixMap = model.getNsPrefixMap();
        nsPrefixMap.forEach((prefix, ns) -> json_documents.add(PrefixedNamespaceToJson(mapper, prefix, ns)));

        //iterate over all subjects(uri resources, blank nodes) and create json doc for each
        for (final ResIterator nodes = model.listSubjects(); nodes.hasNext(); ) {
            json_documents.add(ProcessResource(mapper, nodes.next()));
        }
        //iterate over all objects(uri resources, blank nodes, literals) and create json doc for each
        for (final NodeIterator nodes = model.listObjects(); nodes.hasNext(); ) {
            json_documents.add(ProcessObject(mapper, nodes.next()));
        }

        return json_documents;
    }

    private static String getNextBlankNodeKey(){
        String key = "BLANK_" + blank_node_count;
        blank_node_count++;
        return key;
    }

    private static String getNextNamespaceKey(){
        String key = "NAMESPACE_" + namespace_count;
        namespace_count++;
        return key;
    }

    private static ObjectNode ProcessObject(ObjectMapper mapper, RDFNode node){
        if(node.isLiteral()){
            //handle literal
            ObjectNode json_object = mapper.createObjectNode();
            Literal l = node.asLiteral();
            //TODO add key
            //TODO possibly handle literals of different types seperately
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.LITERAL);
            json_object.put(ArangoAttributes.LITERAL_DATA_TYPE, l.getDatatypeURI());
            json_object.put(ArangoAttributes.LITERAL_VALUE, l.getValue().toString());

            if(l.getDatatypeURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")){
                json_object.put(ArangoAttributes.LITERAL_LANGUAGE, l.getLanguage());
            }

            return json_object;
        }

        //else handle resource
        return ProcessResource(mapper, node.asResource());
    }

    private static ObjectNode ProcessResource(ObjectMapper mapper, Resource res){
        ObjectNode json_object = mapper.createObjectNode();
        if (res.isURIResource()){
            //handle uri
            //TODO check if URI_RESOURCES_MAP already contains uri, if so skip
            //TODO find better hash function that generates unique keys that aren't negative numbers
            String uri = res.getURI();
            int key = uri.hashCode();
            json_object.put(ArangoAttributes.KEY, key);
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.URI_RESOURCE);
            json_object.put(ArangoAttributes.URI, uri);

            //TODO decide whether below namespace and localName attributes are really needed
            json_object.put(ArangoAttributes.NAMESPACE, SplitIRI.namespace(uri));
            json_object.put(ArangoAttributes.URI_LOCAL_NAME, SplitIRI.localname(uri));

            URI_RESOURCES_MAP.put(uri, key);
        }
        else if (res.isAnon()){
            //handle blank node
            //TODO check if BLANK_NODES_MAP already contains uri, if so skip
            String anonId = res.getId().toString();
            String key = getNextBlankNodeKey();
            json_object.put(ArangoAttributes.KEY, key);
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.BLANK_NODE);
            blank_node_count++;
            BLANK_NODES_MAP.put(anonId, key);
        }

        return json_object;
    }

    private static ObjectNode ProcessPredicate(ObjectMapper mapper, Property pred){
        //TODO check if PROPERTIES_MAP already contains uri, if so skip
        ObjectNode json_object = mapper.createObjectNode();
        String uri = pred.getURI();
        int key = uri.hashCode();
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.PROPERTY);
        json_object.put(ArangoAttributes.URI, uri);

        return json_object;
    }

    private static ArrayNode RDFTriplesToJson(ObjectMapper mapper, Model model){
        ArrayNode json_documents = mapper.createArrayNode();

        for (final StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            //TODO
            Statement stmt = stmts.next();
            ObjectNode predicate = ProcessPredicate(mapper, stmt.getPredicate());
            ObjectNode json_edge_object = mapper.createObjectNode();

            //TODO pass subject and object ids/keys in below
            //json_edge_object.put(ArangoAttributes.EDGE_FROM, );
            //json_edge_object.put(ArangoAttributes.EDGE_TO, );
        }

        return  json_documents;
    }

    private static ObjectNode PrefixedNamespaceToJson(ObjectMapper mapper, String prefix, String namespace){
        ObjectNode json_object = mapper.createObjectNode();
        String key = getNextNamespaceKey();
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.NAMESPACE);
        json_object.put(ArangoAttributes.PREFIX, prefix);
        json_object.put(ArangoAttributes.URI, namespace);

        return json_object;
    }
}
