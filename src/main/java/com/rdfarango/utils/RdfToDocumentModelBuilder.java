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
import org.apache.jena.datatypes.xsd.impl.XSDBaseNumericType;
import org.apache.jena.rdf.model.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforms RDF data to JSON - each triple/quad is transformed into a single JSON object
 */
public class RdfToDocumentModelBuilder implements ArangoDbModelDataBuilder {
    private int blank_node_count;
    private Map<String, String> BLANK_NODES_MAP;

    private ArrayNode jsonObjects;

    ObjectMapper mapper = new ObjectMapper();

    public RdfToDocumentModelBuilder(){
        blank_node_count = 0;
        BLANK_NODES_MAP = new HashMap<>();
        jsonObjects = mapper.createArrayNode();
    }

    public RdfToDocumentModelBuilder RDFModelToJson(Model model){
        return this.RDFModelToJson(model, null);
    }

    public RdfToDocumentModelBuilder RDFModelToJson(Model model, String graphName){
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
     */
    @SuppressWarnings("unused")
    public void SaveJsonCollectionsToFiles(){
        try {
            String formattedDate = FileNameUtils.DATE_FORMAT.format(new Date());
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(FileNameUtils.GetValuesFileName(formattedDate)), jsonObjects);
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

    /**
     * Method used to process object nodes in triples
     * @param node
     * @return json object representing the resource in the object position of current triple
     */
    private ObjectNode ProcessObject(RDFNode node){
        if(node.isLiteral()){
            //handle literal
            return TransformUtils.GenerateLiteralJsonObject(mapper, node.asLiteral(), null);
        }
        else {
            //else handle resource
            return ProcessResource(node.asResource());
        }
    }

    /**
     * Method used to process resources in triples (URI or blank node)
     * @param res
     * @return json object representing uri or blank node resource
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

    /**
     * Used to process triples in a Jena model, loops over triples one by one
     * @param model Jena model containing RDF data
     * @param graphName uri identifying named graph, if RDF data was in one
     */
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
     * @return json object representing uri resource
     */
    private ObjectNode ProcessUri(Resource resource){
        //handle uri
        String uri = resource.getURI();

        ObjectNode json_object = mapper.createObjectNode();

        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.IRI);
        json_object.put(ArangoAttributes.VALUE, uri);

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
