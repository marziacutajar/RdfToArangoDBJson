package com.rdfarango.utils;

import java.text.SimpleDateFormat;

public class FileNameUtils {//date format used for generating unique timestamped json file names
    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");

    private static String folder = "results/";
    private static String values_file_name_prefix = "arango_values_";
    private static String resources_file_name_prefix = "arango_resources_";
    private static String literalsFileName_prefix = "arango_literals_";
    private static String edgesFileName_prefix = "arango_edges_";

    private static String jsonFileExtension = ".json";

    public static String GetValuesFileName(String formattedDate){
        return GetFileName(values_file_name_prefix, formattedDate);
    }

    public static String GetResourcesFileName(String formattedDate){
        return GetFileName(resources_file_name_prefix, formattedDate);
    }

    public static String GetEdgesFileName(String formattedDate){
        return GetFileName(edgesFileName_prefix, formattedDate);
    }

    public static String GetLiteralsFileName(String formattedDate){
        return GetFileName(literalsFileName_prefix, formattedDate);
    }

    private static String GetFileName(String fileNamePrefix, String formattedDate){
        return folder + fileNamePrefix + formattedDate + jsonFileExtension;
    }
}
