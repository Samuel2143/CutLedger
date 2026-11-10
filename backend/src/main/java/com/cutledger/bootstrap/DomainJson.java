package com.cutledger.bootstrap;

import com.cutledger.rights.RightsEngine.Scope;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DomainJson {
    private final ObjectMapper mapper;
    private static final Set<String> SET_FIELDS=Set.of("distributions","territories","platforms","reasonCodes","clauseIds");
    public DomainJson(ObjectMapper mapper){this.mapper=mapper;}
    public JsonNode parse(Object value){try{return mapper.readTree(value.toString());}catch(Exception e){throw new IllegalStateException("Invalid stored snapshot");}}
    public String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Invalid snapshot");}}
    public JsonNode tree(Object value){return mapper.valueToTree(value);}
    public Map<String,Object> object(Object value){return mapper.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}
    public String hash(Object value){return hashText(write(canonical(tree(value),"")));}
    public String hashText(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException();}}
    private Object canonical(JsonNode node,String field){
        if(node.isObject()){var result=new TreeMap<String,Object>();node.fields().forEachRemaining(e->result.put(e.getKey(),canonical(e.getValue(),e.getKey())));return result;}
        if(node.isArray()){var result=new ArrayList<Object>();node.forEach(n->result.add(canonical(n,"")));if(SET_FIELDS.contains(field))result.sort(Comparator.comparing(Object::toString));return result;}
        return node;
    }
    public Set<String> strings(JsonNode node){var result=new TreeSet<String>();if(!node.isArray())throw new IllegalStateException("Invalid stored set");node.forEach(n->result.add(n.asText()));return result;}
    public Scope scope(JsonNode node){
        if(node.path("schemaVersion").asInt()!=1)throw new IllegalStateException("Unsupported snapshot version");
        return new Scope(strings(node.path("distributions")),strings(node.path("territories")),strings(node.path("platforms")),LocalDate.parse(node.path("startDate").asText()),LocalDate.parse(node.path("endDateExclusive").asText()));
    }
    public Map<String,Object> scopeSnapshot(Scope scope){return Map.of("schemaVersion",1,"distributions",new TreeSet<>(scope.distributions()),"territories",new TreeSet<>(scope.territories()),"platforms",new TreeSet<>(scope.platforms()),"startDate",scope.startDate().toString(),"endDateExclusive",scope.endDateExclusive().toString());}
}
