package com.rdfarango.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.ArangoAttributes;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.SplitIRI;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO add comments for every important part

public class RdfToJsonBuilder {
    private int blank_node_count;
    private int namespace_count;

    private Map<String, Integer> URI_RESOURCES_MAP;
    private Map<Literal, Integer> LITERALS_MAP;
    private Map<String, String> BLANK_NODES_MAP;

    private ArrayNode jsonValues;
    private ArrayNode jsonEdges;

    ObjectMapper mapper = new ObjectMapper();

    public RdfToJsonBuilder(){
        blank_node_count = 0;
        namespace_count = 0;
        URI_RESOURCES_MAP = new HashMap<>();
        LITERALS_MAP = new HashMap<>();
        BLANK_NODES_MAP = new HashMap<>();
        jsonValues = mapper.createArrayNode();
        jsonEdges = mapper.createArrayNode();
    }

    public RdfToJsonBuilder RDFModelToJson(Model model){
        ProcessNamespaces(model);
        ProcessSubjects(model);
        ProcessObjects(model);
        ProcessTriples(model);

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
        //iterate over all subjects(uri resources, blank nodes) and create json doc for each
        for (final ResIterator nodes = model.listSubjects(); nodes.hasNext(); ) {
            ProcessResource(nodes.next());
        }
    }

    private void ProcessObjects(Model model){
        //iterate over all objects(uri resources, blank nodes, literals) and create json doc for each
        for (final NodeIterator nodes = model.listObjects(); nodes.hasNext(); ) {
            ProcessObject(nodes.next());
        }
    }

    private void ProcessObject(RDFNode node){
        if(node.isLiteral()){
            //handle literal
            ObjectNode json_object = mapper.createObjectNode();
            Literal l = node.asLiteral();

            int key = l.hashCode();
            json_object.put(ArangoAttributes.KEY, key);
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.LITERAL);
            json_object.put(ArangoAttributes.LITERAL_DATA_TYPE, l.getDatatypeURI());

            var literalType = l.getDatatype();
            if(literalType instanceof XSDAbstractDateTimeType || literalType instanceof XSDBaseStringType){
                json_object.put(ArangoAttributes.LITERAL_VALUE, l.getString());
            }
            else if (literalType instanceof RDFLangString){
                json_object.put(ArangoAttributes.LITERAL_VALUE, l.getString());
                json_object.put(ArangoAttributes.LITERAL_LANGUAGE, l.getLanguage());
            }
            else{
                json_object.putPOJO(ArangoAttributes.LITERAL_VALUE, l.getValue());
            }

            LITERALS_MAP.put(l, key);

            jsonValues.add(json_object);
        }
        else {
            //else handle resource
            ProcessResource(node.asResource());
        }
    }

    private void ProcessResource(Resource res){
        if (res.isURIResource()){
            ProcessUri(res);
        }
        else if (res.isAnon()){
            //handle blank node
            String anonId = res.getId().toString();
            if(BLANK_NODES_MAP.containsKey(anonId))
                return;

            ObjectNode json_object = mapper.createObjectNode();

            String key = getNextBlankNodeKey();
            json_object.put(ArangoAttributes.KEY, key);
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.BLANK_NODE);
            blank_node_count++;
            BLANK_NODES_MAP.put(anonId, key);

            jsonValues.add(json_object);
        }
    }

    private void ProcessTriples(Model model){
        for (final StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            Statement stmt = stmts.next();
            Property prop = stmt.getPredicate();
            //TODO consider not adding JSON docs for predicates that aren't subjects in other triples
            //ProcessUri(prop);

            AddEdgeDocument(getResourceKey(stmt.getSubject()), getObjectKey(stmt.getObject()), prop.getURI());
        }
    }

    private void ProcessUri(Resource resource){
        //handle uri
        String uri = resource.getURI();
        if(URI_RESOURCES_MAP.containsKey(uri))
            return;

        int key = uri.hashCode();
        ObjectNode json_object = mapper.createObjectNode();
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.URI);
        json_object.put(ArangoAttributes.URI, uri);

        //TODO decide whether below namespace and localName attributes are really needed
        //json_object.put(ArangoAttributes.NAMESPACE, SplitIRI.namespace(uri));
        //json_object.put(ArangoAttributes.URI_LOCAL_NAME, SplitIRI.localname(uri));

        URI_RESOURCES_MAP.put(uri, key);
        jsonValues.add(json_object);
    }

    private void AddEdgeDocument(String subjectKey, String objectKey, String predicateUri){
        ObjectNode json_edge_object = mapper.createObjectNode();

        //when importing into arango, we will then tell it to append a prefix (collection name) to _from and _to values
        json_edge_object.put(ArangoAttributes.EDGE_FROM, subjectKey);
        json_edge_object.put(ArangoAttributes.EDGE_TO, objectKey);
        json_edge_object.put(ArangoAttributes.EDGE_PREDICATE, predicateUri);

        jsonEdges.add(json_edge_object);
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

    private String getResourceKey(Resource res){
        if(res.isAnon())
            return BLANK_NODES_MAP.get(res.getURI());

        return URI_RESOURCES_MAP.get(res.getURI()).toString();
    }

    private String getObjectKey(RDFNode node){
        if(node.isLiteral())
            return LITERALS_MAP.get(node.asLiteral()).toString();

        return getResourceKey(node.asResource());
    }
}
