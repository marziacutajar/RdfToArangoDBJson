package com.rdfarango.utils;

public class Hasher {

    public static int HashString(String stringIn){
        var hash = 0;
        if (stringIn.length() == 0) return hash;
        for (int i = 0; i < stringIn.length(); i++) {
            var c = Character.codePointAt(stringIn, i);
            hash = ((hash << 5) - hash) + c;
            hash = hash & hash; // Convert to 32bit integer
        }
        return (hash + 2147483647) + 1;
    }

}
