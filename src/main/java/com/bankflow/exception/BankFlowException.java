package com.bankflow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BankFlowException extends RuntimeException {
    private final HttpStatus status;

    public BankFlowException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
