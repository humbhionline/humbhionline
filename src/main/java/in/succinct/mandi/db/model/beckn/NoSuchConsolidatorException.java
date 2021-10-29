package in.succinct.mandi.db.model.beckn;

public class NoSuchConsolidatorException extends RuntimeException {
    public NoSuchConsolidatorException(String action) {
        super("Action:" + action);
    }
}
