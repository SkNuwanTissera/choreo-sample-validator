package com.wso2.choreosamples.validator;

public class ValidatorException extends Exception {

    public ValidatorException(String message, Throwable e) {
        super(message, e);
    }

    public ValidatorException(String message) {
        super(message);
    }
}
