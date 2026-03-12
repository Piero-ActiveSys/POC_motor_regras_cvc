package br.com.cvc.poc.engine.model;

import java.util.Map;

public class Rule {

    private Map<String,String> conditions;
    private String value;
    private String type;

    public Rule(Map<String,String> conditions,String value,String type){
        this.conditions=conditions;
        this.value=value;
        this.type=type;
    }

    public String get(String key){
        return conditions.get(key);
    }

    public String getValue(){
        return value;
    }

    public String getType(){
        return type;
    }

}