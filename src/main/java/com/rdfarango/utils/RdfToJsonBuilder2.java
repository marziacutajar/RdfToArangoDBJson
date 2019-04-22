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

public class RdfToJsonBuilder2 {
    private int blank_node_count;
    private Map<String, String> BLANK_NODES_MAP;

    private ArrayNode jsonValues;

    ObjectMapper mapper = new ObjectMapper();

    public RdfToJsonBuilder2(){
        blank_node_count = 0;
        BLANK_NODES_MAP = new HashMap<>();
        jsonValues = mapper.createArrayNode();
    }

    public RdfToJsonBuilder2 RDFModelToJson(Model model, String graphName){
        ProcessTriples(model, graphName);
        return this;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonValuesCollection(){
        return jsonValues;
    }

    @SuppressWarnings("unused")
    public void SaveJsonCollectionsToFiles(String valuesFilePath){
        try {
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(valuesFilePath), jsonValues);
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

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

    private ObjectNode ProcessResource(Resource res){
        if (res.isURIResource()){
            return ProcessUri(res, false);
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
            json_triple.set("s", ProcessResource(stmt.getSubject()));
            json_triple.set("p", ProcessUri(stmt.getPredicate(), true));
            json_triple.set("o", ProcessObject(stmt.getObject()));

            if(!StringUtils.isBlank(graphName))
                json_triple.put("g", graphName);

            jsonValues.add(json_triple);
        }
    }

    private ObjectNode ProcessUri(Resource resource, boolean isPredicate){
        //handle uri
        String uri = resource.getURI();

        ObjectNode json_object = mapper.createObjectNode();

        //predicates are obviously uris
        if(!isPredicate)
            json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.URI);
        json_object.put(ArangoAttributes.URI, uri);

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
