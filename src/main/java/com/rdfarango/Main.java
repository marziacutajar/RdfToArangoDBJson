package com.rdfarango;

import com.rdfarango.utils.RdfToJsonBuilder;
import org.apache.commons.cli.*;
import org.apache.jena.rdf.model.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

    public static void main(String[] args) {
        // create the parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
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

            // save resulting json documents to file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
            String formattedDate = sdf.format(new Date());
            String valuesFileName = "results/arango_values_" + formattedDate + ".json";
            String edgesFileName = "results/arango_edges_" + formattedDate + ".json";
            builder.SaveJsonCollectionsToFiles(valuesFileName, edgesFileName);
        }
        catch(ParseException exp) {
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
    }
}
