package com.rdfarango;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rdfarango.constants.RdfObjectTypes;
import com.rdfarango.utils.Hasher;
import com.rdfarango.utils.RdfToJsonBuilder;
import org.apache.commons.cli.*;
import org.apache.jena.rdf.model.*;
import com.rdfarango.constants.ArangoAttributes;
import org.apache.jena.util.SplitIRI;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//TODO add comments for every important part

public class Main {

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
            RdfToJsonBuilder builder = new RdfToJsonBuilder();
            builder.RDFModelToJson(model);
            builder.SaveJsonCollectionsToFiles("arango_values.json", "arango_edges.json");
        }
        catch(ParseException exp) {
            // oops, something went wrong
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
    }
}
