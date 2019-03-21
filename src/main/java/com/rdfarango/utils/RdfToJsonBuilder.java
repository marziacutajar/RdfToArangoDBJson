package com.rdfarango.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.ArangoAttributes;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.SplitIRI;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RdfToJsonBuilder {
    private int blank_node_count;
    private int namespace_count;

    private Map<String, Integer> URI_RESOURCES_MAP;
    private Map<String, String> LITERALS_MAP;
    private Map<String, String> BLANK_NODES_MAP;
    private Map<String, String> PROPERTIES_MAP;
    //TODO properties can't also be used as subjects/objects, so if this is encountered in data, return error to user

    private ArrayNode jsonValues;
    private ArrayNode jsonEdges;

    ObjectMapper mapper = new ObjectMapper();

    public RdfToJsonBuilder(){
        blank_node_count = 0;
        namespace_count = 0;
        URI_RESOURCES_MAP = new HashMap<>();
        LITERALS_MAP = new HashMap<>();
        BLANK_NODES_MAP = new HashMap<>();
        PROPERTIES_MAP = new HashMap<>();
        jsonValues = mapper.createArrayNode();
        jsonEdges = mapper.createArrayNode();
    }

    public RdfToJsonBuilder RDFModelToJson(Model model){
        ProcessNamespaces(model);
        ProcessSubjects(model);
        ProcessObjects(model);

        return this;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonValuesCollection(){
        return jsonValues;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonEdgesCollection(){
        return jsonEdges;
    }

    @SuppressWarnings("unused")
    public void SaveJsonCollectionsToFiles(String valuesFilePath, String edgesFilePath){
        try {
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(valuesFilePath), jsonValues);
            writer.writeValue(new File(edgesFilePath), jsonEdges);
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

    private void ProcessNamespaces(Model model){
        //iterate over all namespaces and create json doc for each
        Map<String, String> nsPrefixMap = model.getNsPrefixMap();
        nsPrefixMap.forEach((prefix, ns) -> jsonValues.add(PrefixedNamespaceToJson(prefix, ns)));
    }

    private ObjectNode PrefixedNamespaceToJson(String prefix, String namespace){
        ObjectNode json_object = mapper.createObjectNode();
        String key = getNextNamespaceKey();
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.NAMESPACE);
        json_object.put(ArangoAttributes.PREFIX, prefix);
        json_object.put(ArangoAttributes.URI, namespace);

        return json_object;
    }

    private void ProcessSubjects(Model model){
        //iterate over all objects(uri resources, blank nodes, literals) and create json doc for each
        for (final NodeIterator nodes = model.listObjects(); nodes.hasNext(); ) {
            jsonValues.add(ProcessObject(nodes.next()));
        }
    }

    private void ProcessObjects(Model model){
        //iterate over all subjects(uri resources, blank nodes) and create json doc for each
        for (final ResIterator nodes = model.listSubjects(); nodes.hasNext(); ) {
            jsonValues.add(ProcessResource(nodes.next()));
        }
    }

    private ObjectNode ProcessObject(RDFNode node){
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
        return ProcessResource(node.asResource());
    }

    private ObjectNode ProcessResource(Resource res){
        ObjectNode json_object = mapper.createObjectNode();
        if (res.isURIResource()){
            //handle uri
            //TODO check if URI_RESOURCES_MAP already contains uri, if so skip
            //TODO find better hash function that generates unique keys that aren't negative numbers
            String uri = res.getURI();
            int key = Hasher.HashString(uri);
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

    private ObjectNode ProcessPredicate(Property pred){
        //TODO check if PROPERTIES_MAP already contains uri, if so skip
        ObjectNode json_object = mapper.createObjectNode();
        String uri = pred.getURI();
        int key = Hasher.HashString(uri);
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.PROPERTY);
        json_object.put(ArangoAttributes.URI, uri);

        return json_object;
    }

    private ArrayNode ProcessTriples(Model model){
        ArrayNode json_documents = mapper.createArrayNode();

        for (final StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            //TODO
            Statement stmt = stmts.next();
            ObjectNode predicate = ProcessPredicate(stmt.getPredicate());
            ObjectNode json_edge_object = mapper.createObjectNode();

            //TODO pass subject and object ids/keys in below
            //json_edge_object.put(ArangoAttributes.EDGE_FROM, );
            //json_edge_object.put(ArangoAttributes.EDGE_TO, );
        }

        return  json_documents;
    }

    private String getNextBlankNodeKey(){
        String key = "BLANK_" + blank_node_count;
        blank_node_count++;
        return key;
    }

    private String getNextNamespaceKey(){
        String key = "NAMESPACE_" + namespace_count;
        namespace_count++;
        return key;
    }
}
