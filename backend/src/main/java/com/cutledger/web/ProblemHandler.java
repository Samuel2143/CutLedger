package com.cutledger.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> missing(Exception ignored,HttpServletRequest request) { return problem(Problem.notFound(),request); }
    @ExceptionHandler(Problem.class)
    ResponseEntity<?> problem(Problem p, HttpServletRequest request) {
        return ResponseEntity.status(p.status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Map.of("type","about:blank","status",p.status,"title",p.code,"code",p.code,
                "detail",p.getMessage(),"traceId",String.valueOf(request.getAttribute("traceId"))));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception ignored, HttpServletRequest request) {
        return problem(new Problem(400,"INVALID_REQUEST","Check the submitted fields."),request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unavailable(Exception ignored, HttpServletRequest request) {
        // Do not log exception messages: third-party exceptions can contain private input.
        return problem(new Problem(503,"SERVICE_UNAVAILABLE","The service is unavailable. Retry this existing request."),request);
    }
}
