package in.succinct.mandi.db.model.beckn;

import com.venky.core.util.Bucket;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.OnCancel;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.OnInit;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Request;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.BecknExtnAttributes;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.Reader;
import java.util.List;

public interface BecknMessage extends Model {
    @UNIQUE_KEY
    public String getMessageId();
    public void setMessageId(String messageId);

    public String getCallBackUri();
    void setCallBackUri(String callBackUri);

    @COLUMN_SIZE(4098)
    public String getRequestPayload();
    public void setRequestPayload(String payload);

    public long getExpirationTime();
    public void setExpirationTime(long l);



    public Reader getResponse();
    public void setResponse(Reader response);


    public Bucket getNumPendingResponses();
    public void setNumPendingResponses(Bucket pendingResponses);

    public List<ServerResponse> getServerResponses();

    public ResponseConsolidator getConsolidator();

    void setRequestPath(String target);
    public String getRequestPath();

    public static abstract class ResponseConsolidator extends BecknAsyncTask {
        BecknMessage message ;

        public ResponseConsolidator(BecknMessage message){
            super(getRequest(message));
            this.message = message;
        }
        public static BecknMessage getMessage(String messageId) {
            List<BecknMessage> messageList = new Select().from(BecknMessage.class).where(new Expression(ModelReflector.instance(BecknMessage.class).getPool(),"MESSAGE_ID", Operator.EQ, messageId)).execute();
            BecknMessage message = null;
            if (messageList.size() == 1){
                message = messageList.get(0);
            }
            return message;
        }
        public static Request getRequest(BecknMessage message){
            Request request =  message == null ? new Request() : new Request(message.getRequestPayload());
            if (message != null){
                request.getExtendedAttributes().set(BecknExtnAttributes.CALLBACK_URL,message.getCallBackUri());
            }
            return request;
        }
        public Request executeInternal(){
            JSONObject consolidated  = null;
            for (ServerResponse response : message.getServerResponses()) {
                JSONObject current = (JSONObject) JSONValue.parse(response.getResponse());

                if (consolidated == null) {
                    consolidated = current;
                } else if (current != null){
                    consolidate(consolidated, current);
                }
            }
            validateResponse(consolidated);
            Request request = new Request(consolidated);
            request.getContext().setBppId(BecknNetwork.findByRetailBppUrl(message.getRequestPath()).getRetailBppSubscriberId());
            request.getContext().setBppUri(Config.instance().getServerBaseUrl() + "/bpp");
            return request;
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {

        }

        public abstract void consolidate(JSONObject consolidatedResponse, JSONObject aResponse);

    }
    public static class SearchConsolidator extends ResponseConsolidator {

        public SearchConsolidator(String messageId) {
            this(getMessage(messageId));
        }
        public SearchConsolidator(BecknMessage message){
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnSearch onSearch = new OnSearch(consolidatedResponse);
            Providers providers = onSearch.getMessage().getCatalog().getProviders();

            OnSearch aResp = new OnSearch(aResponse);
            for (Provider p : aResp.getMessage().getCatalog().getProviders()){
                providers.add(p);
            }
        }

    }
    public static class SelectConsolidator extends ResponseConsolidator {

        public SelectConsolidator(String messageId) {
            this(getMessage(messageId));
        }
        public SelectConsolidator(BecknMessage message){
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnSelect consolidated = new OnSelect(consolidatedResponse);
            OnSelect shardResponse = new OnSelect(aResponse);
            if (shardResponse.getMessage() != null && shardResponse.getMessage().getOrder() != null){
                consolidated.setInner(shardResponse.getInner());
            }
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {
            OnSelect consolidated = new OnSelect(consolidatedResponse);
            if (consolidated.getMessage().getOrder() == null){
                throw new RuntimeException("Cannot identify item(s) passed!");
            }
        }

    }
    public static class InitConsolidator extends ResponseConsolidator {

        public InitConsolidator(String messageId) {
            this(getMessage(messageId));
        }
        public InitConsolidator(BecknMessage message){
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnInit consolidated = new OnInit(consolidatedResponse);
            OnInit shardResponse = new OnInit(aResponse);
            if (shardResponse.getMessage() != null && shardResponse.getMessage().getOrder() != null){
                consolidated.setInner(shardResponse.getInner());
            }
        }

        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {
            OnInit consolidated = new OnInit(consolidatedResponse);
            if (consolidated.getMessage().getOrder() == null){
                throw new RuntimeException("Cannot identify item(s) passed!");
            }
        }
    }
    public static class ConfirmConsolidator extends ResponseConsolidator {

        public ConfirmConsolidator(String messageId) {
            this(getMessage(messageId));
        }

        public ConfirmConsolidator(BecknMessage message) {
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnConfirm consolidated = new OnConfirm(consolidatedResponse);
            OnConfirm shardResponse = new OnConfirm(aResponse);
            if (shardResponse.getMessage() != null && shardResponse.getMessage().getOrder() != null){
                consolidated.setInner(shardResponse.getInner());
            }
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {
            OnConfirm consolidated = new OnConfirm(consolidatedResponse);
            if (consolidated.getMessage().getOrder() == null){
                throw new RuntimeException("Cannot identify transaction Id!");
            }
        }
    }
    public static class CancelConsolidator extends ResponseConsolidator {

        public CancelConsolidator(String messageId) {
            this(getMessage(messageId));
        }
        public CancelConsolidator(BecknMessage message){
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnCancel consolidated = new OnCancel(consolidatedResponse);
            OnCancel shardResponse = new OnCancel(aResponse);
            if (shardResponse.getMessage() != null && shardResponse.getMessage().getOrder() != null){
                consolidated.setInner(shardResponse.getInner());
            }
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {
            OnCancel consolidated = new OnCancel(consolidatedResponse);
            if (consolidated.getMessage().getOrder() == null){
                throw new RuntimeException("Cannot identify transaction Id!");
            }
        }
    }
    public static class StatusConsolidator extends ResponseConsolidator {

        public StatusConsolidator(String messageId) {
            this(getMessage(messageId));
        }
        public StatusConsolidator(BecknMessage message){
            super(message);
        }

        @Override
        public void consolidate(JSONObject consolidatedResponse, JSONObject aResponse) {
            OnStatus consolidated = new OnStatus(consolidatedResponse);
            OnStatus shardResponse = new OnStatus(aResponse);
            if (shardResponse.getMessage() != null && shardResponse.getMessage().getOrder() != null){
                consolidated.setInner(shardResponse.getInner());
            }
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {
            OnStatus consolidated = new OnStatus(consolidatedResponse);
            if (consolidated.getMessage().getOrder() == null){
                throw new RuntimeException("Cannot identify transaction Id!");
            }
        }
    }
}
