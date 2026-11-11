package com.cutledger.interpretation;

import com.cutledger.rights.RightsEngine.Scope;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

/** Validates untrusted proposed edits. It never chooses a tier, price or payment action. */
@Component
public final class InterpretationValidator {
    public static final class InvalidOutput extends RuntimeException {
        public final String code;
        InvalidOutput(String code){super(code);this.code=code;}
    }
    public record Span(String field,String quote,int start,int end){}
    public record Issue(String id,String field,String kind,String message){}
    public record Unsupported(String id,String requirement,List<Span> evidence){public Unsupported(String requirement,List<Span> evidence){this("unsupported-"+UUID.nameUUIDFromBytes(requirement.getBytes(java.nio.charset.StandardCharsets.UTF_8)),requirement,evidence);}public Unsupported{evidence=List.copyOf(evidence);}}
    public record Result(String status,Scope normalizedCandidate,Map<String,String> provenance,List<Issue> issues,List<Unsupported> unsupportedRequests,List<Span> evidence){
        public Result{provenance=Map.copyOf(provenance);issues=List.copyOf(issues);unsupportedRequests=List.copyOf(unsupportedRequests);evidence=List.copyOf(evidence);}
    }
    private final ObjectMapper mapper;
    private final JsonNode schema;
    private static final Set<String> ISO=Set.of(Locale.getISOCountries());
    private static final Set<String> PLATFORMS=Set.of("INSTAGRAM","YOUTUBE","WEBSITE");
    public InterpretationValidator(){
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(4000).build()).build();
        mapper=new ObjectMapper(factory);
        try(var stream=getClass().getResourceAsStream("/ai/AI_OUTPUT.schema.json")){
            if(stream==null)throw new IllegalStateException("Canonical AI schema missing");schema=mapper.readTree(stream);
        }catch(IOException failure){throw new IllegalStateException("Canonical AI schema unavailable");}
    }
    public JsonNode parse(String output){
        if(output==null||output.length()>65536)throw new InvalidOutput("OUTPUT_SIZE_LIMIT");
        try(var parser=mapper.createParser(output)){
            JsonNode value=mapper.readTree(parser);
            if(value==null||parser.nextToken()!=null)throw new InvalidOutput("INVALID_JSON");
            validateSchema(value,schema);return value;
        }catch(IOException failure){throw new InvalidOutput("INVALID_JSON");}
    }
    private void validateSchema(JsonNode value,JsonNode rule){
        var type=rule.path("type");boolean matches=false;
        for(var item:type.isArray()?type:mapper.createArrayNode().add(type))matches|=switch(item.asText()){
            case "object"->value.isObject();case "array"->value.isArray();case "string"->value.isTextual();case "integer"->value.isIntegralNumber();case "null"->value.isNull();default->false;
        };
        if(!matches)throw new InvalidOutput("SCHEMA_TYPE");
        if(rule.has("const")&&!rule.get("const").equals(value))throw new InvalidOutput("SCHEMA_CONST");
        if(rule.has("enum")){boolean found=false;for(var item:rule.get("enum"))found|=item.equals(value);if(!found)throw new InvalidOutput("SCHEMA_ENUM");}
        if(value.isObject()){
            for(var required:rule.path("required"))if(!value.has(required.asText()))throw new InvalidOutput("SCHEMA_REQUIRED");
            var names=value.fieldNames();while(names.hasNext()){
                String name=names.next();var property=rule.path("properties").get(name);
                if(property==null){if(rule.path("additionalProperties").isBoolean()&&!rule.path("additionalProperties").asBoolean())throw new InvalidOutput("SCHEMA_EXTRA_PROPERTY");}
                else validateSchema(value.get(name),property);
            }
        }
        if(value.isArray()){
            if(rule.has("maxItems")&&value.size()>rule.get("maxItems").asInt())throw new InvalidOutput("SCHEMA_ARRAY_LIMIT");
            for(var item:value)validateSchema(item,rule.path("items"));
        }
        if(value.isTextual()){
            int length=value.textValue().codePointCount(0,value.textValue().length());
            if((rule.has("minLength")&&length<rule.get("minLength").asInt())||(rule.has("maxLength")&&length>rule.get("maxLength").asInt()))throw new InvalidOutput("SCHEMA_STRING_LIMIT");
        }
        if(value.isIntegralNumber()&&(!value.canConvertToLong()||(rule.has("minimum")&&value.longValue()<rule.get("minimum").longValue())||(rule.has("maximum")&&value.longValue()>rule.get("maximum").longValue())))throw new InvalidOutput("SCHEMA_NUMBER_LIMIT");
    }
    public Result validate(String brief,Scope base,LocalDate anchor,JsonNode edits){
        if(brief==null||brief.isBlank()||brief.length()>4000||base==null||anchor==null)throw new IllegalArgumentException("Invalid extraction context");
        validateSchema(edits,schema);
        var issues=new ArrayList<Issue>();var evidence=new ArrayList<Span>();var provenance=new LinkedHashMap<String,String>();var unsupported=new ArrayList<Unsupported>();
        Set<String> distribution=apply("DISTRIBUTION",base.distributions(),edits.path("distributionEdit"),brief,evidence,issues,provenance);
        Set<String> territory=apply("TERRITORY",base.territories(),edits.path("territoryEdit"),brief,evidence,issues,provenance);
        Set<String> platform=apply("PLATFORM",base.platforms(),edits.path("platformEdit"),brief,evidence,issues,provenance);
        if(!(territory.equals(Set.of("WORLDWIDE"))||ISO.containsAll(territory)))issue(issues,"TERRITORY","AMBIGUOUS","Specify ISO country codes or WORLDWIDE alone.");
        if(!PLATFORMS.containsAll(platform)){
            var unknown=new TreeSet<>(platform);unknown.removeAll(PLATFORMS);
            unsupported.add(new Unsupported("Unsupported platform: "+String.join(", ",unknown),evidence.stream().filter(s->s.field.equals("PLATFORM")).toList()));
        }
        var date=edits.path("dateEdit");
        boolean dateEdited=List.of("startDate","endDateExclusive","durationDays","durationMonths").stream().anyMatch(name->!date.path(name).isNull());
        spans("DATES",date.path("evidence"),brief,evidence,issues);
        if(dateEdited&&date.path("evidence").isEmpty())throw new InvalidOutput("MISSING_DATE_EVIDENCE");
        provenance.put("DATES",dateEdited?date.path("certainty").asText():"INHERITED");
        provenance.put("START_DATE",date.path("startDate").isNull()?"INHERITED":date.path("certainty").asText());
        provenance.put("END_DATE",!date.path("endDateExclusive").isNull()?date.path("certainty").asText():!date.path("durationDays").isNull()||!date.path("durationMonths").isNull()?"CALCULATED":"INHERITED");
        if(dateEdited&&!date.path("certainty").asText().equals("EXPLICIT"))issue(issues,"DATES","AMBIGUOUS","Review the proposed dates explicitly.");
        LocalDate start=date.path("startDate").isNull()?base.startDate():iso(date.path("startDate").asText());
        LocalDate end=date.path("endDateExclusive").isNull()?null:iso(date.path("endDateExclusive").asText());
        try{
            var calculated=new ArrayList<LocalDate>();
            if(!date.path("durationDays").isNull())calculated.add(start.plusDays(date.path("durationDays").longValue()));
            if(!date.path("durationMonths").isNull())calculated.add(start.plusMonths(date.path("durationMonths").longValue()));
            if(end!=null)calculated.add(end);
            if(calculated.stream().distinct().count()>1)issue(issues,"DURATION","CONFLICT","The supplied duration and end dates disagree.");
            if(end==null)end=calculated.isEmpty()?base.endDateExclusive():calculated.get(0);
        }catch(DateTimeException|ArithmeticException failure){throw new InvalidOutput("INVALID_DATE_RANGE");}
        long days=ChronoUnit.DAYS.between(start,end);
        if(days<1||days>365||start.isBefore(anchor)||end.isAfter(anchor.plusDays(365)))unsupported.add(new Unsupported("Dates outside the merchant's fixed campaign window",evidence.stream().filter(s->s.field.equals("DATES")).toList()));
        for(var requirement:edits.path("unsupportedRequests")){
            int begin=evidence.size();spans("UNSUPPORTED",requirement.path("evidence"),brief,evidence,issues);
            if(requirement.path("evidence").isEmpty())throw new InvalidOutput("MISSING_UNSUPPORTED_EVIDENCE");
            unsupported.add(new Unsupported(requirement.path("requirement").asText(),evidence.subList(begin,evidence.size())));
        }
        for(var reported:edits.path("issues")){
            int begin=evidence.size();spans(reported.path("field").asText(),reported.path("evidence"),brief,evidence,issues);
            if(reported.path("kind").asText().equals("UNSUPPORTED")){
                if(reported.path("evidence").isEmpty())throw new InvalidOutput("MISSING_UNSUPPORTED_EVIDENCE");
                unsupported.add(new Unsupported(reported.path("message").asText(),evidence.subList(begin,evidence.size())));
            }
            issue(issues,reported.path("field").asText(),reported.path("kind").asText(),reported.path("message").asText());
        }
        var scope=new Scope(distribution,territory,platform,start,end);
        String status=!unsupported.isEmpty()?"UNSUPPORTED":issues.isEmpty()?"READY":"NEEDS_CLARIFICATION";
        return new Result(status,scope,provenance,issues,unsupported,evidence);
    }
    private Set<String> apply(String field,Set<String> base,JsonNode edit,String brief,List<Span> evidence,List<Issue> issues,Map<String,String> provenance){
        String operation=edit.path("operation").asText();spans(field,edit.path("evidence"),brief,evidence,issues);
        if(operation.equals("UNCHANGED")){
            if(!edit.path("values").isEmpty())throw new InvalidOutput("UNCHANGED_HAS_VALUES");
            provenance.put(field,"INHERITED");return Set.copyOf(base);
        }
        if(edit.path("evidence").isEmpty())throw new InvalidOutput("MISSING_EDIT_EVIDENCE");
        if(edit.path("values").isEmpty())throw new InvalidOutput("EMPTY_EDIT_VALUES");
        provenance.put(field,edit.path("certainty").asText());
        if(!edit.path("certainty").asText().equals("EXPLICIT"))issue(issues,field,"AMBIGUOUS","Review the proposed change explicitly.");
        var values=new TreeSet<String>();for(var value:edit.path("values")){
            String name=value.asText().strip().toUpperCase(Locale.ROOT);
            if(field.equals("TERRITORY"))name=switch(name){case "UK"->"GB";case "USA"->"US";default->name;};values.add(name);
        }
        if(field.equals("TERRITORY")&&base.equals(Set.of("WORLDWIDE"))&&operation.equals("REMOVE")&&!values.equals(Set.of("WORLDWIDE")))issue(issues,field,"AMBIGUOUS","WORLDWIDE with country exclusions cannot be represented by this policy.");
        var result=new TreeSet<>(base);
        switch(operation){case "REPLACE"->{result.clear();result.addAll(values);}case "ADD"->result.addAll(values);case "REMOVE"->result.removeAll(values);default->throw new InvalidOutput("INVALID_OPERATION");}
        if(result.isEmpty())issue(issues,field,"MISSING","The effective scope cannot be empty.");
        if(field.equals("DISTRIBUTION")&&result.contains("PAID_ADS")&&!operation.equals("REMOVE")){
            for(var quote:edit.path("evidence"))if(quote.asText().toLowerCase(Locale.ROOT).matches("(?s).*\\b(no|not|never|without|don't)\\b.{0,60}\\b(paid|advertising|ads)\\b.*"))issue(issues,field,"CONFLICT","The paid-ad evidence contains negation; confirm a correction.");
        }
        return Set.copyOf(result);
    }
    private void spans(String field,JsonNode quotes,String brief,List<Span> spans,List<Issue> issues){
        for(var item:quotes){String quote=item.asText();int start=brief.indexOf(quote);if(start<0)throw new InvalidOutput("FABRICATED_EVIDENCE");
            spans.add(new Span(field,quote,start,start+quote.length()));
            if(brief.indexOf(quote,start+1)>=0)issue(issues,field,"AMBIGUOUS","The evidence occurs more than once; clarify its intended span.");
        }
    }
    private static LocalDate iso(String value){
        if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new InvalidOutput("INVALID_ISO_DATE");
        try{return LocalDate.parse(value);}catch(DateTimeParseException failure){throw new InvalidOutput("INVALID_ISO_DATE");}
    }
    private static void issue(List<Issue> issues,String field,String kind,String message){issues.add(new Issue("issue-"+issues.size(),field,kind,message));}
}
