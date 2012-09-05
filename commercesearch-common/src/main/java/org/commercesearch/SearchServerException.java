package org.commercesearch;

public class SearchServerException extends Exception {

    private static final long serialVersionUID = 4939401119787557866L;

    public SearchServerException(String msg) {
        super(msg);
    }

    public SearchServerException(String msg, Throwable ex) {
        super(msg, ex);
    }
}
