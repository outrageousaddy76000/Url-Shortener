package com.adarsh.urlshortener.util;

public class Base62 {

    private static final String CHARS="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String encode(long value){

        if(value==0){
            return "0";
        }

        StringBuilder sb=new StringBuilder();

        while(value>0){
            sb.append(CHARS.charAt((int)(value%62)));
            value/=62;
        }

        return sb.reverse().toString();
    }

}
