package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.table.ModelImpl;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.beckn.BecknMessage.CancelConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.ConfirmConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.InitConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.ResponseConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.SearchConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.SelectConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.StatusConsolidator;

public class BecknMessageImpl extends ModelImpl<BecknMessage> {
    public BecknMessageImpl(BecknMessage message){
        super(message);
    }
    public BecknMessageImpl(){
        super();
    }

    public ResponseConsolidator getConsolidator() {
        BecknMessage message = getProxy();
        Request request = new Request(message.getRequestPayload());
        ResponseConsolidator consolidator ;
        switch (request.getContext().getAction()){
            case "select":
                consolidator = new SelectConsolidator(message);
                break;
            case "search":
                consolidator = new SearchConsolidator(message);
                break;
            case "init":
                consolidator = new InitConsolidator(message);
                break;
            case "confirm":
                consolidator = new ConfirmConsolidator(message);
                break;
            case "cancel":
                consolidator = new CancelConsolidator(message);
                break;
            case "status":
                consolidator = new StatusConsolidator(message);
                break;
            default:
                consolidator = null;
                break;
        }
        return consolidator;

    }
}
