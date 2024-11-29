package com.hmdp.common.exception;

public class AccountExistException extends BaseException{
    public AccountExistException() {
    }

    public AccountExistException(String message) {
        super(message);
    }
}
