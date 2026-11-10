package com.cutledger.payment;
import java.util.*;
public enum PaymentState {
    CREATING_ORDER,AWAITING_APPROVAL,CAPTURE_COMMITTED,CAPTURE_PENDING,RECONCILING,SETTLED,FAILED,CANCELLED,REVIEW_REQUIRED;
    public boolean allows(PaymentState next){
        if(this==next)return true;
        return switch(this){
            case CREATING_ORDER->Set.of(AWAITING_APPROVAL,RECONCILING,FAILED,CANCELLED).contains(next);
            case AWAITING_APPROVAL->Set.of(CAPTURE_COMMITTED,CANCELLED,FAILED,REVIEW_REQUIRED).contains(next);
            case CAPTURE_COMMITTED->Set.of(CAPTURE_PENDING,RECONCILING,SETTLED,FAILED,REVIEW_REQUIRED).contains(next);
            case CAPTURE_PENDING->Set.of(RECONCILING,SETTLED,FAILED,REVIEW_REQUIRED).contains(next);
            case RECONCILING->Set.of(AWAITING_APPROVAL,CAPTURE_PENDING,SETTLED,FAILED,REVIEW_REQUIRED,CANCELLED).contains(next);
            case SETTLED->next==REVIEW_REQUIRED;
            default->false;
        };
    }
    public boolean terminal(){return Set.of(SETTLED,FAILED,CANCELLED,REVIEW_REQUIRED).contains(this);}
}
