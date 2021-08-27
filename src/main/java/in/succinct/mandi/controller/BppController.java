package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Option;
import in.succinct.beckn.Options;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.BecknExtnAttributes;
import in.succinct.mandi.agents.beckn.Cancel;
import in.succinct.mandi.agents.beckn.Confirm;
import in.succinct.mandi.agents.beckn.Init;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.agents.beckn.Select;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.beckn.BecknMessage;
import in.succinct.mandi.db.model.beckn.ServerResponse;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.Cipher;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class BppController extends Controller {
    public BppController(Path path) {
        super(path);
    }

    public View nack(Request request,String realm){
        Acknowledgement nack = new Acknowledgement(Status.NACK);
        //nack.setSignature(Request.generateSignature(request.hash(),request.getPrivateKey(request.getContext().getBppId(),request.getContext().getBppId() +".k1")));

        return new BytesView(getPath(),
                new Response(request.getContext(),new Acknowledgement(Status.NACK)).getInner().toString().getBytes(StandardCharsets.UTF_8),
                MimeType.APPLICATION_JSON,"WWW-Authenticate","Signature realm=\""+realm+"\"",
                "headers=\"(created) (expires) digest\""){
            @Override
            public void write() throws IOException {
                super.write(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
    }
    public View ack(Request request){
        Acknowledgement ack = new Acknowledgement(Status.ACK);
        return new BytesView(getPath(),new Response(request.getContext(),ack).getInner().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
    }
    public String getGatewayUrl(Map<String,String> authParams){
        String url = null;
        if (!authParams.isEmpty()){
            String keyId = authParams.get("keyId");
            StringTokenizer keyTokenizer = new StringTokenizer(keyId,"|");
            String subscriberId = keyTokenizer.nextToken();
            JSONArray subscriber = BecknPublicKeyFinder.lookup(subscriberId);
            if (!subscriber.isEmpty()){
                url = (String) ((JSONObject)subscriber.get(0)).get("subscriber_url");
            }
        }
        return url;
    }
    protected boolean isRequestAuthenticated(Request request){
        Map<String,String> headers = getPath().getHeaders();

        Application application = getPath().getApplication();
        if (application == null){
            String callbackUrl = getGatewayUrl(request.extractAuthorizationParams("Proxy-Authorization",headers));
            if (callbackUrl == null) {
                callbackUrl = request.getContext().getBapUri();
            }
            request.getExtendedAttributes().set(BecknExtnAttributes.CALLBACK_URL,callbackUrl);

            return request.verifySignature("Proxy-Authorization",headers , false) &&
                    request.verifySignature("Authorization",headers , Config.instance().getBooleanProperty("beckn.auth.enabled", false));
        }else {
            ServerNode node = ServerNode.findNode(application.getAppId());
            if (node != null){
                request.getExtendedAttributes().set(BecknExtnAttributes.CALLBACK_URL,node.getBaseUrl() +"/" + "beckn_messages");
                request.getExtendedAttributes().set(BecknExtnAttributes.INTERNAL,true);
                return true;
            }
        }
        return false;
    }

    private <C extends BecknAsyncTask> View act(Class<C> clazzTask){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            request.getContext().setBppId(BecknUtil.getSubscriberId());
            request.getContext().setBppUri(Config.instance().getServerBaseUrl() + "/bpp");

            if (isRequestAuthenticated(request)){
                if (clazzTask != null){
                    if (!request.getExtendedAttributes().getBoolean(BecknExtnAttributes.INTERNAL)){
                        List<ServerNode> shards = new com.venky.swf.sql.Select().from(ServerNode.class).execute();
                        if (shards.isEmpty()){
                            TaskManager.instance().executeAsync(clazzTask.getConstructor(Request.class).newInstance(request), false);
                        }else {
                            submitInternalRequestToShards(shards,request);
                        }
                    }else{
                        TaskManager.instance().executeAsync(clazzTask.getConstructor(Request.class).newInstance(request), false);
                    }
                }
                return ack(request);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    private void submitInternalRequestToShards(List<ServerNode> nodes, Request request) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        BecknMessage message = Database.getTable(BecknMessage.class).newRecord();
        message.setMessageId(request.getContext().getMessageId());
        message = Database.getTable(BecknMessage.class).getRefreshed(message);
        message.setExpirationTime(System.currentTimeMillis() + request.getContext().getTtl() * 1000L);
        message.setNumPendingResponses(new Bucket(nodes.size()));
        message.setCallBackUri(request.getExtendedAttributes().get(BecknExtnAttributes.CALLBACK_URL));
        message.setRequestPayload(request.toString());
        message.save();

        List<Task> tasks = new ArrayList<>();
        ServerNode self = ServerNode.selfNode();
        String token = Base64.getEncoder().encodeToString(String.format("%s:%s", self.getClientId(), self.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", String.format("Basic %s", token));

        for (ServerNode node : nodes) {
            ServerResponse response = Database.getTable(ServerResponse.class).newRecord();
            response.setBecknMessageId(message.getId());
            response.setServerNodeId(node.getId());
            response = Database.getTable(ServerResponse.class).getRefreshed(response);
            response.save();
            tasks.add((Task) () -> {
                try {
                    new Call<InputStream>().url(node.getBaseUrl() + "/bpp/" + request.getContext().getAction()).headers(headers).
                            inputFormat(InputFormat.INPUT_STREAM).
                            input(getPath().getInputStream()).method(HttpMethod.POST).getResponseAsJson();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        TaskManager.instance().executeAsync(tasks,false);
    }

    @RequireLogin(false)
    public View search() {
        return act(Search.class);
    }
    @RequireLogin(false)
    public View select(){
        return act(Select.class);
    }
    @RequireLogin(false)
    public View cancel(){
        return act(Cancel.class);
    }

    @RequireLogin(false)
    public View init(){
        return act(Init.class);
    }
    @RequireLogin(false)
    public View confirm(){
        return act(Confirm.class);
    }

    @RequireLogin(false)
    public View status(){
        return act(in.succinct.mandi.agents.beckn.Status.class);
    }

    @RequireLogin(false)
    public View update(){
        return act(Update.class);
    }


    @RequireLogin(false)
    public View track(){
        return act(null);
    }

    @RequireLogin(false)
    public View rating(){
        return act(null);
    }

    @RequireLogin(false)
    public View support(){
        return act(null);
    }

    @RequireLogin(false)
    public View get_cancellation_reasons(){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (!Config.instance().getBooleanProperty("beckn.auth.enabled", false) ||
                request.verifySignature("Authorization",getPath().getHeaders())){
                List<OrderCancellationReason> reasons = new com.venky.swf.sql.Select().from(OrderCancellationReason.class).execute();
                Options options = new Options();
                reasons.forEach(r->{
                    if (r.isUsableBeforeDelivery()){
                        Option option = new Option();
                        option.setId(BecknUtil.getBecknId(String.valueOf(r.getId()),
                                Entity.cancellation_reason));
                        Descriptor descriptor = new Descriptor();
                        option.setDescriptor(descriptor);
                        descriptor.setName(r.getReason());
                        descriptor.setCode(String.valueOf(r.getId()));
                        options.add(option);
                    }
                });
                return new BytesView(getPath(),options.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

    @RequireLogin(false)
    public View get_return_reasons(){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (!Config.instance().getBooleanProperty("beckn.auth.enabled", false) ||
                    request.verifySignature("Authorization",getPath().getHeaders())){
                List<OrderCancellationReason> reasons = new com.venky.swf.sql.Select().from(OrderCancellationReason.class).execute();
                Options options = new Options();
                reasons.forEach(r->{
                    if (r.isUsableAfterDelivery()){
                        Option option = new Option();
                        option.setId(BecknUtil.getBecknId(String.valueOf(r.getId()),
                                Entity.cancellation_reason));
                        Descriptor descriptor = new Descriptor();
                        option.setDescriptor(descriptor);
                        descriptor.setName(r.getReason());
                        descriptor.setCode(String.valueOf(r.getId()));
                        options.add(option);
                    }
                });
                return new BytesView(getPath(),options.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

    @RequireLogin(false)
    public View get_rating_categories(){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (request.verifySignature("Authorization",getPath().getHeaders())){
                return new BytesView(getPath(),new JSONArray().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

    @RequireLogin(value = false)
    public View on_subscribe() throws Exception{
        String payload = StringUtil.read(getPath().getInputStream());
        JSONObject object = (JSONObject) JSONValue.parse(payload);
        byte[] encrypted = Base64.getDecoder().decode((String)object.get("challenge"));
        if (!ObjectUtil.equals(object.get("subscriber_id"), Config.instance().getHostName())){
            return IntegrationAdaptor.instance(SWFHttpResponse.class,JSONObject.class).createStatusResponse(getPath(),new RuntimeException("Subscriber id mismatch"),"Subscriber Mismatch");
        }

        if (!Request.verifySignature(getPath().getHeader("Signature"), payload,
                new Request().getPublicKey(Config.instance().getProperty("beckn.registry.id"),Config.instance().getProperty("beckn.registry.id")+".k1"))){
            throw new RuntimeException("Cannot verify Signature");
        }

        String privateKey = BecknUtil.getSelfEncryptionKey().getPrivateKey();

        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, Crypt.getInstance().getPrivateKey(Crypt.KEY_ALGO,privateKey));

        byte[] decrypted = cipher.doFinal(encrypted);

        JSONObject output = new JSONObject();
        output.put("answer", new String(decrypted));

        return new BytesView(getPath(),output.toString().getBytes());
    }

}
