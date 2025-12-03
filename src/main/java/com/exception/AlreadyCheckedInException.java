package com.exception;

// package com.service 또는 com.exception 등
public class AlreadyCheckedInException extends RuntimeException {
    public AlreadyCheckedInException(String message) {
        super(message);
    }
}

