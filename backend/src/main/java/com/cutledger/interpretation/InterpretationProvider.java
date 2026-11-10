package com.cutledger.interpretation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface InterpretationProvider {
    record ExtractionInput(Map<String,Object> context,String repairCode){}
    record ExtractionResult(String text,String model,String referenceHash,long latencyMs,JsonNode usage){}
    final class Failure extends RuntimeException {
        public final String code;public final boolean transientFailure;public final int retryAfterSeconds;public final Map<String,Object> details;
        public Failure(String code,boolean transientFailure,int retryAfterSeconds){this(code,transientFailure,retryAfterSeconds,Map.of());}
        public Failure(String code,boolean transientFailure,int retryAfterSeconds,Map<String,Object> details){super(code);this.code=code;this.transientFailure=transientFailure;this.retryAfterSeconds=Math.min(86400,Math.max(1,retryAfterSeconds));this.details=Map.copyOf(details);}
    }
    ExtractionResult extract(ExtractionInput input);
}
