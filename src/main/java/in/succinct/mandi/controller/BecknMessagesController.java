package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.OnCancel;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.OnInit;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.BecknExtnAttributes;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.beckn.BecknMessage;
import in.succinct.mandi.db.model.beckn.ServerResponse;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.bouncycastle.cert.ocsp.Req;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class BecknMessagesController extends ModelController<BecknMessage> {
    public BecknMessagesController(Path path) {
        super(path);
    }

    private ServerNode getNode(){
        Application application = getPath().getApplication();
        if (application == null){
            throw new AccessDeniedException("Unauthorized Request");
        }
        ServerNode fromNode = ServerNode.findNode(application.getAppId());

        if (fromNode == null){
            throw new AccessDeniedException("AppId doesnot belong to a valid ServerNode");
        }
        return fromNode;
    }
    private <T extends ResponseConsolidator> View update_response(Class<T> responseConsolidator) {
        try {
            ServerNode fromNode = getNode();

            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            List<BecknMessage> messages = new Select(true,true).from(BecknMessage.class).where(
                    new Expression(ModelReflector.instance(BecknMessage.class).getPool(),"MESSAGE_ID",Operator.EQ,request.getContext().getMessageId())).execute(1);
            if (messages.size() != 1){
                throw new RuntimeException("Unknown messageId");
            }
            BecknMessage message = messages.get(0);
            message.getNumPendingResponses().decrement();
            message.save();

            ServerResponse response = Database.getTable(ServerResponse.class).newRecord();
            response.setBecknMessageId(message.getId());
            response.setServerNodeId(fromNode.getId());
            response = Database.getTable(ServerResponse.class).getRefreshed(response);
            if (response.getRawRecord().isNewRecord()) {
                throw new RuntimeException("Unknown ServerNodeId");
            }
            response.setResponse(new InputStreamReader(getPath().getInputStream()));
            response.save();
            if (responseConsolidator != null && message.getNumPendingResponses().intValue() == 0) {
                TaskManager.instance().executeAsync(responseConsolidator.getConstructor(String.class).newInstance(message.getMessageId()),false);
            }

            return ack(request);
        }catch (IOException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex){
            throw new RuntimeException(ex);
        }
    }
    public View ack(Request request){
        Acknowledgement ack = new Acknowledgement(Status.ACK);
        return new BytesView(getPath(),new Response(request.getContext(),ack).toString().getBytes(StandardCharsets.UTF_8));
    }

    @RequireLogin(false)
    public View on_search() {
        return update_response(SearchConsolidator.class);
    }

    @RequireLogin(false)
    public View on_select(){
        return update_response(SelectConsolidator.class);
    }

    @RequireLogin(false)
    public View on_init(){
        return update_response(InitConsolidator.class);
    }

    @RequireLogin(false)
    public View on_confirm(){
        return update_response(ConfirmConsolidator.class);
    }

    @RequireLogin(false)
    public View on_status(){
        return update_response(StatusConsolidator.class);
    }

    @RequireLogin(false)
    public View on_cancel(){
        return update_response(CancelConsolidator.class);
    }

    @RequireLogin(false)
    public View on_update(){
        return update_response(null);
    }

    @RequireLogin(false)
    public View on_track(){
        return update_response(null);
    }

    @RequireLogin(false)
    public View on_rating(){
        return update_response(null);
    }

    @RequireLogin(false)
    public View on_support(){
        return update_response(null);
    }

    public static abstract class ResponseConsolidator extends BecknAsyncTask {
        String messageId ;
        BecknMessage message ;
        public ResponseConsolidator(String messageId){
            this(getMessage(messageId));
        }
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
                } else {
                    consolidate(consolidated, current);
                }
            }
            validateResponse(consolidated);
            Request request = new Request(consolidated);
            request.getContext().setBppId(BecknUtil.getSubscriberId());
            request.getContext().setBppUri(Config.instance().getServerBaseUrl() + "/bpp");
            return request;
        }
        public void validateResponse(JSONObject consolidatedResponse) throws RuntimeException {

        }

        public abstract void consolidate(JSONObject consolidatedResponse, JSONObject aResponse);

    }
    public static class SearchConsolidator extends ResponseConsolidator {

        public SearchConsolidator(String messageId) {
            super(messageId);
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
            super(messageId);
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
            super(messageId);
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
            super(messageId);
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
            super(messageId);
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
            super(messageId);
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
