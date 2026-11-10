package com.cutledger.web;

public class Problem extends RuntimeException {
    public final int status;
    public final String code;
    public Problem(int status, String code, String detail) { super(detail); this.status=status; this.code=code; }
    public static Problem notFound() { return new Problem(404,"NOT_FOUND","The requested record is unavailable."); }
}
