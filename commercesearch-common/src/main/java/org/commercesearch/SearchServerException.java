package org.commercesearch;

/**
 * This class implements provide the Exception implementation for the SearchServer API
 * 
 * @author rmerizalde
 */
public abstract class SearchServerException extends Exception {

    private static final long serialVersionUID = 4939401119787557866L;

    public static enum Code {
        CORE_RELOAD_EXCEPTION,
        EXPORT_SYNONYM_EXCEPTION,
        INDEX_RULES_EXCEPTION,
        SEARCH_EXCEPTION,
        UPDATE_EXCEPTION,
        COMMIT_EXCEPTION,
        PING_EXCEPTION;
    }

    private Code code;

    public Code getCode() {
        return code;
    }

    public void setCode(Code code) {
        this.code = code;
    }

    public SearchServerException(Code code, Throwable throwable) {
        super(throwable);
        this.code = code;
    }

    public static SearchServerException create(Code code) {
        return create(code, null);
    }

    public static SearchServerException create(Code code, Throwable throwable) {
        switch (code) {
            case CORE_RELOAD_EXCEPTION:
                return new CoreReloadException(code, throwable);
            case EXPORT_SYNONYM_EXCEPTION:
                return new ExportSynonymException(code, throwable);
            case INDEX_RULES_EXCEPTION:
                return new IndexRulesException(code, throwable);
            case SEARCH_EXCEPTION:
                return new SearchException(code, throwable);
            case UPDATE_EXCEPTION:
                return new UpdateException(code, throwable);
            case COMMIT_EXCEPTION:
                return new CommitException(code, throwable);
            case PING_EXCEPTION:
                return new PingException(code, throwable);
            default:
                throw new IllegalArgumentException("Invalid exception code");
        }
    }

    public static class CoreReloadException extends SearchServerException {

        public CoreReloadException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class ExportSynonymException extends SearchServerException {

        public ExportSynonymException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class IndexRulesException extends SearchServerException {

        public IndexRulesException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class SearchException extends SearchServerException {

        public SearchException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class UpdateException extends SearchServerException {

        public UpdateException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class CommitException extends SearchServerException {

        public CommitException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }

    public static class PingException extends SearchServerException {

        public PingException(Code code, Throwable throwable) {
            super(code, throwable);
        }

    }
}