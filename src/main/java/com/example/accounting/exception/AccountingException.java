package com.example.accounting.exception;

/** 会計ドメインの不変条件やルール違反を表す例外。 */
public class AccountingException extends RuntimeException {

    public AccountingException(String message) {
        super(message);
    }

    public AccountingException(String message, Throwable cause) {
        super(message, cause);
    }
}
