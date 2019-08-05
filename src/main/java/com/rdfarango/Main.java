package com.rdfarango;

import com.rdfarango.utils.RdfToJsonBuilder;
import com.rdfarango.utils.RdfToJsonBuilder2;
import org.apache.commons.cli.*;
import org.apache.jena.base.Sys;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.sse.SSE;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

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
            String fileName = line.getOptionValue("f");
            model.read(fileName);

            System.out.println("Parsing RDF into JSON...");
            RdfToJsonBuilder builder = new RdfToJsonBuilder();

            //to handle triples in different named graphs, we need to use Dataset, not one Model
            //then iterate over all named (and default) models in the dataset and create triples
            Dataset dataset = RDFDataMgr.loadDataset(fileName);
            Iterator<String> namedGraphs = dataset.listNames();
            builder.RDFModelToJson(dataset.getDefaultModel(), null);
            while (namedGraphs.hasNext()) {
                String namedGraph = namedGraphs.next();
                builder.RDFModelToJson(dataset.getNamedModel(namedGraph), namedGraph);
            }

            // save resulting json documents to file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
            String formattedDate = sdf.format(new Date());
            String valuesFileName = "results/arango_values_" + formattedDate + ".json";
            String edgesToResourcesFileName = "results/arango_edges_resources_" + formattedDate + ".json";
            String edgesToLiteralsFileName = "results/arango_edges_literals_" + formattedDate + ".json";
            String literalsFileName = "results/arango_literals_" + formattedDate + ".json";
            builder.SaveJsonCollectionsToFiles(valuesFileName, literalsFileName, edgesToResourcesFileName, edgesToLiteralsFileName);
            //builder.SaveJsonCollectionsToFiles(valuesFileName);
        }
        catch(ParseException exp) {
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
    }
}
