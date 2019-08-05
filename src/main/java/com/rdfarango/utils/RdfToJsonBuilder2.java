package com.rdfarango.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.ArangoAttributes;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.datatypes.xsd.impl.XSDAbstractDateTimeType;
import org.apache.jena.datatypes.xsd.impl.XSDBaseStringType;
import org.apache.jena.rdf.model.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO add comments for every important part

/**
 * Transforms RDF data to JSON - each triple/quad is transformed into a single JSON object
 */
public class RdfToJsonBuilder2 {
    private int blank_node_count;
    private Map<String, String> BLANK_NODES_MAP;

    private ArrayNode jsonObjects;

    ObjectMapper mapper = new ObjectMapper();

    public RdfToJsonBuilder2(){
        blank_node_count = 0;
        BLANK_NODES_MAP = new HashMap<>();
        jsonObjects = mapper.createArrayNode();
    }

    public RdfToJsonBuilder2 RDFModelToJson(Model model, String graphName){
        ProcessTriples(model, graphName);
        return this;
    }

    /**
     * Returns array of json objects, where each object represents a triple/quad
     * @return array of json objects
     */
    @SuppressWarnings("unused")
    public ArrayNode GetJsonObjectsCollection(){
        return jsonObjects;
    }

    /**
     * Save the array of json objects representing all the triples/quads to file
     * @param valuesFilePath - path to file where to save json objects
     */
    @SuppressWarnings("unused")
    public void SaveJsonCollectionsToFiles(String valuesFilePath){
        try {
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(valuesFilePath), jsonObjects);
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

    /**
     * Method used to process object nodes in triples
     * @param node
     * @return
     */
    private ObjectNode ProcessObject(RDFNode node){
        if(node.isLiteral()){
            //handle literal
            ObjectNode json_object = mapper.createObjectNode();
            Literal l = node.asLiteral();

            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.LITERAL);
            json_object.put(ArangoAttributes.LITERAL_DATA_TYPE, l.getDatatypeURI());

            RDFDatatype literalType = l.getDatatype();
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

            return json_object;
        }
        else {
            //else handle resource
            return ProcessResource(node.asResource());
        }
    }

    /**
     * Method used to process resources in triples (URI or blank node)
     * @param res
     * @return
     */
    private ObjectNode ProcessResource(Resource res){
        if (res.isURIResource()){
            return ProcessUri(res);
        }
        else if (res.isAnon()){
            //handle blank node
            String anonId = res.getId().toString();

            ObjectNode json_object = mapper.createObjectNode();

            String key = getNextBlankNodeKey();
            json_object.put(ArangoAttributes.KEY, key);
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.BLANK_NODE);
            blank_node_count++;
            BLANK_NODES_MAP.put(anonId, key);

            return json_object;
        }
        return null;
    }

    private void ProcessTriples(Model model, String graphName){
        for (final StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            Statement stmt = stmts.next();

            ObjectNode json_triple = mapper.createObjectNode();
            json_triple.set(ArangoAttributes.SUBJECT, ProcessResource(stmt.getSubject()));
            json_triple.set(ArangoAttributes.PREDICATE, ProcessUri(stmt.getPredicate()));
            json_triple.set(ArangoAttributes.OBJECT, ProcessObject(stmt.getObject()));

            if(!StringUtils.isBlank(graphName))
                json_triple.put(ArangoAttributes.GRAPH_NAME, graphName);

            jsonObjects.add(json_triple);
        }
    }

    /**
     * Method used to process URIs in triples
     * @param resource representing a subject/predicate/object of a triple
     * @return
     */
    private ObjectNode ProcessUri(Resource resource){
        //handle uri
        String uri = resource.getURI();

        ObjectNode json_object = mapper.createObjectNode();

        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.IRI);
        json_object.put(ArangoAttributes.IRI, uri);

        //TODO decide whether below namespace and localName attributes are really needed
        //json_object.put(ArangoAttributes.NAMESPACE, SplitIRI.namespace(uri));
        //json_object.put(ArangoAttributes.URI_LOCAL_NAME, SplitIRI.localname(uri));

        return json_object;
    }

    private String getNextBlankNodeKey(){
        String key = "BLANK_" + blank_node_count;
        blank_node_count++;
        return key;
    }
}
