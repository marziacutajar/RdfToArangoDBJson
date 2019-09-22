package com.rdfarango.utils;

import org.apache.http.MethodNotSupportedException;
import org.apache.jena.rdf.model.Model;

public interface ArangoDbModelDataBuilder {
    ArangoDbModelDataBuilder RDFModelToJson(Model model);
    ArangoDbModelDataBuilder RDFModelToJson(Model model, String graphName);
    void SaveJsonCollectionsToFiles();
}
