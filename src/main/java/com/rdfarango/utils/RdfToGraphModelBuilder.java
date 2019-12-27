package com.rdfarango.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.ArangoAttributes;
import com.rdfarango.constants.Configuration;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class RdfToGraphModelBuilder implements ArangoDbModelDataBuilder{

    private Map<String, String> URI_RESOURCES_MAP;
    private Map<Literal, String> LITERALS_MAP;
    private Map<String, String> BLANK_NODES_MAP;

    private ArrayNode jsonLiterals;
    private ArrayNode jsonEdgesToResources;
    private ArrayNode jsonEdgesToLiterals;
    private List<ObjectNode> jsonResources;

    private int BLANK_NODE_COUNT;
    private int CURR_LITERAL_KEY;

    ObjectMapper mapper = new ObjectMapper();

    private String currentGraphName;

    public RdfToGraphModelBuilder(){
        URI_RESOURCES_MAP = new HashMap<>();
        LITERALS_MAP = new HashMap<>();
        BLANK_NODES_MAP = new HashMap<>();
        jsonResources = new ArrayList<>();
        jsonLiterals = mapper.createArrayNode();
        jsonEdgesToResources = mapper.createArrayNode();
        jsonEdgesToLiterals = mapper.createArrayNode();
        BLANK_NODE_COUNT = 0;
        //we get the start key for literals from config file, in case the user wants to add literals
        //to an existing ArangoDB collection and needs to avoid key collisions
        CURR_LITERAL_KEY = Configuration.GetGraphLiteralsStartKey();
    }

    public RdfToGraphModelBuilder RDFModelToJson(Model model){
        return this.RDFModelToJson(model, null);
    }

    public RdfToGraphModelBuilder RDFModelToJson(Model model, String graphName){
        currentGraphName = graphName;
        ProcessSubjects(model);
        ProcessObjects(model);
        ProcessTriples(model);

        return this;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonResourcesCollection(){
        return mapper.createArrayNode().addAll(jsonResources);
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonLiteralsCollection(){
        return jsonLiterals;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonEdgesToResourcesCollection(){
        return jsonEdgesToResources;
    }

    @SuppressWarnings("unused")
    public ArrayNode GetJsonEdgesToLiteralsCollection(){
        return jsonEdgesToLiterals;
    }

    @SuppressWarnings("unused")
    public void SaveJsonCollectionsToFiles(){
        try {
            String formattedDate = FileNameUtils.DATE_FORMAT.format(new Date());
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(FileNameUtils.GetResourcesFileName(formattedDate)), jsonResources);
            writer.writeValue(new File(FileNameUtils.GetLiteralsFileName(formattedDate)), jsonLiterals);
            writer.writeValue(new File(FileNameUtils.GetEdgesToResourcesFileName(formattedDate)), jsonEdgesToResources);
            writer.writeValue(new File(FileNameUtils.GetEdgesToLiteralsFileName(formattedDate)), jsonEdgesToLiterals);
        }
        catch(IOException exp){
            System.err.println("Error while creating JSON file. Reason: " + exp.getMessage());
        }
    }

    private void ProcessSubjects(Model model){
        //iterate over all subjects(uri resources, blank nodes) and create json object for each
        for (final ResIterator nodes = model.listSubjects(); nodes.hasNext(); ) {
            ProcessResource(nodes.next());
        }
    }

    private void ProcessObjects(Model model){
        //iterate over all objects(uri resources, blank nodes, literals) and create json object for each
        for (final NodeIterator nodes = model.listObjects(); nodes.hasNext(); ) {
            ProcessObject(nodes.next());
        }
    }

    private void ProcessObject(RDFNode node){
        if(node.isLiteral()){
            //handle literal
            Literal l = node.asLiteral();

            String key = getNextLiteralKey();
            LITERALS_MAP.put(l, key);
            jsonLiterals.add(TransformUtils.GenerateLiteralJsonObject(mapper, l, key));
        }
        else {
            //handle resource
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
            json_object.put(ArangoAttributes.VALUE, anonId);
            BLANK_NODES_MAP.put(anonId, key);

            jsonResources.add(json_object);
        }
    }

    private void ProcessTriples(Model model){
        for (final StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            Statement stmt = stmts.next();
            Property prop = stmt.getPredicate();
            //ProcessUri(prop);

            String resourceKey = getResourceKey(stmt.getSubject());

            AddEdgeDocument(resourceKey, stmt.getObject(), prop.getURI());
        }
    }

    private void ProcessUri(Resource resource){
        //handle uri
        String uri = resource.getURI();
        if(URI_RESOURCES_MAP.containsKey(uri))
            return;

        String key = String.valueOf(uri.hashCode());
        ObjectNode json_object = mapper.createObjectNode();
        json_object.put(ArangoAttributes.KEY, key);
        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.IRI);
        json_object.put(ArangoAttributes.VALUE, uri);

        URI_RESOURCES_MAP.put(uri, key);
        jsonResources.add(json_object);
    }

    private void AddEdgeDocument(String subjectKey, RDFNode object, String predicateUri){
        ObjectNode json_edge_object = mapper.createObjectNode();

        //when importing into arango, we will then tell it to append a prefix (collection name) to _from and _to values
        json_edge_object.put(ArangoAttributes.EDGE_FROM, subjectKey);
        json_edge_object.put(ArangoAttributes.EDGE_TO, getObjectKey(object));

        ObjectNode predicate_json_object = mapper.createObjectNode();
        predicate_json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.IRI);
        //in the future, if we create seperate vertices for all predicate uris, consider setting this to the id/key of the predicate's vertex
        predicate_json_object.put(ArangoAttributes.VALUE, predicateUri);

        json_edge_object.set(ArangoAttributes.EDGE_PREDICATE, predicate_json_object);

        if(!StringUtils.isBlank(currentGraphName))
            json_edge_object.put(ArangoAttributes.GRAPH_NAME, currentGraphName);

        if(object.isLiteral())
            jsonEdgesToLiterals.add(json_edge_object);
        else
            jsonEdgesToResources.add(json_edge_object);
    }

    private String getNextBlankNodeKey(){
        String key = "BLANK_" + BLANK_NODE_COUNT;
        BLANK_NODE_COUNT++;
        return key;
    }

    private String getNextLiteralKey(){
        String key = String.valueOf(CURR_LITERAL_KEY);
        CURR_LITERAL_KEY++;
        return key;
    }

    private String getResourceKey(Resource res){
        if(res.isAnon())
            return BLANK_NODES_MAP.get(res.getId().toString());

        return URI_RESOURCES_MAP.get(res.getURI());
    }

    private String getObjectKey(RDFNode node){
        if(node.isLiteral())
            return LITERALS_MAP.get(node.asLiteral());

        return getResourceKey(node.asResource());
    }
}
