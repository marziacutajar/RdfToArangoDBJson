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

        System.out.println("Starting algebra test...");

        //initialise ARQ before making any calls to Jena, otherwise running jar file throws exception
        ARQ.init();

        //Testing example of ARQ sparql algebra
        //TODO this QueryFactory.create part is VERY SLOWWW... find solution (it's not the string concatenation... so IDK!!!!)

        try {
            Query query = QueryFactory.create("PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
                    "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n" +
                    "\n" +
                    "SELECT ?who ?g ?mbox\n" +
                    "FROM <http://example.org/dft.ttl>\n" +
                    "FROM NAMED <http://example.org/alice>\n" +
                    "FROM NAMED <http://example.org/bob>\n" +
                    "WHERE\n" +
                    "{\n" +
                    "   ?g dc:publisher ?who .\n" +
                    "   GRAPH ?g { ?x foaf:mbox ?mbox }\n" +
                    "}");

        System.out.println("getting graphs");

        //testing how to get FROM and FROM NAMED uris
        query.getNamedGraphURIs().forEach(f-> System.out.println(f)); //get all FROM NAMED uris
        query.getGraphURIs().forEach(f-> System.out.println(f)); //get all FROM uris (forming default graph)

        System.out.println("generating algebra");

        Op op = Algebra.compile(query);

        System.out.println("writing algebra");

        SSE.write(op);

        //TODO possibly use below tutorial for visitor pattern to translate algebra tree
        //https://www.codeproject.com/Articles/1241363/Expression-Tree-Traversal-Via-Visitor-Pattern-in-P

        }
        catch(QueryException qe){
            System.out.println("Invalid SPARQL query.");
        }

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            Model model = ModelFactory.createDefaultModel() ;

            System.out.println("Reading RDF file...");
            String fileName = line.getOptionValue("f");
            model.read(fileName) ;

            System.out.println("Parsing RDF into JSON...");
            RdfToJsonBuilder2 builder = new RdfToJsonBuilder2();

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
            //String edgesFileName = "results/arango_edges_" + formattedDate + ".json";
            //builder.SaveJsonCollectionsToFiles(valuesFileName, edgesFileName);
            builder.SaveJsonCollectionsToFiles(valuesFileName);
        }
        catch(ParseException exp) {
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
    }
}
