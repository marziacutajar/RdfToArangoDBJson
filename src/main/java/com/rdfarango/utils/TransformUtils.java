package com.rdfarango.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.ArangoAttributes;
import com.rdfarango.constants.RdfObjectTypes;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.datatypes.xsd.impl.XSDBaseNumericType;
import org.apache.jena.rdf.model.Literal;

public class TransformUtils {

    public static ObjectNode GenerateLiteralJsonObject(ObjectMapper mapper, Literal l, String _key){
        ObjectNode json_object = mapper.createObjectNode();

        //if _key is specified, use it, otherwise ArangoDB will auto-generate it on import
        if(_key != null)
            json_object.put(ArangoAttributes.KEY, _key);

        json_object.put(ArangoAttributes.TYPE, RdfObjectTypes.LITERAL);
        json_object.put(ArangoAttributes.LITERAL_DATA_TYPE, l.getDatatypeURI());

        RDFDatatype literalType = l.getDatatype();
        if(literalType instanceof XSDBaseNumericType)
            json_object.put(ArangoAttributes.VALUE, l.getDouble());
        else
            json_object.put(ArangoAttributes.VALUE, l.getString());

        if(literalType instanceof RDFLangString){
            json_object.put(ArangoAttributes.LITERAL_LANGUAGE, l.getLanguage());
        }

        return json_object;
    }
}
