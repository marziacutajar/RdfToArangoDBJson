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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

        //iterate over all objects(uri resources, blank nodes, literals) and create json doc for each
        for (final NodeIterator nodes = model.listObjects(); nodes.hasNext(); ) {
            RDFNode node = nodes.next();
            ObjectNode json_object = mapper.createObjectNode();
            if(node.isLiteral()){
                //handle literal
                Literal l = node.asLiteral();
                //TODO add key
                //TODO possibly handle literals of different types seperately
                json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.LITERAL);
                json_object.put(ArangoAttributes.LITERAL_DATA_TYPE, l.getDatatypeURI());
                json_object.put(ArangoAttributes.LITERAL_VALUE, l.getValue().toString());

                if(l.getDatatypeURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")){
                    json_object.put(ArangoAttributes.LITERAL_LANGUAGE, l.getLanguage());
                }
            }
            else {
                //handle resource
                Resource res = node.asResource();
                if (res.isURIResource()){
                    //handle uri
                    //TODO make sure below hashCode function generates unique keys
                    //TODO hashCode is generating negative ints... restrict to +ve
                    String uri = res.getURI();
                    int key = uri.hashCode();
                    json_object.put(ArangoAttributes.KEY, key);
                    json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.URI_RESOURCE);
                    json_object.put(ArangoAttributes.URI, uri);

                    //TODO below namespace and localName are incorrect
                    json_object.put(ArangoAttributes.NAMESPACE, res.getNameSpace());
                    json_object.put(ArangoAttributes.URI_LOCAL_NAME, res.getLocalName());
                    URI_RESOURCES_MAP.put(uri, key);

                }
                else if (res.isAnon()){
                    //handle blank node
                    String anonId = res.getId().toString();
                    String key = getNextBlankNodeKey();
                    json_object.put(ArangoAttributes.KEY, key);
                    json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.BLANK_NODE);
                    blank_node_count++;
                    BLANK_NODES_MAP.put(anonId, key);
                }
            }
            json_documents.add(json_object);
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

    private static ArrayNode RDFTriplesToJson(ObjectMapper mapper, Model model){
        ArrayNode json_documents = mapper.createArrayNode();

        for (final StmtIterator nodes = model.listStatements(); nodes.hasNext(); ) {

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
