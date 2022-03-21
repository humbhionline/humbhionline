package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.ProxySubscriberImpl;
import com.venky.swf.plugins.collab.db.model.CryptoKey;
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
import in.succinct.beckn.Subscriber;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.BecknExtnAttributes;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.beckn.BecknMessage;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.db.model.beckn.ServerResponse;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.util.InternalNetwork;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
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

        return new BytesView(getPath(),
                new Response(null,new Acknowledgement(Status.NACK)).getInner().toString().getBytes(StandardCharsets.UTF_8),
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
        return new BytesView(getPath(),new Response(null,ack).getInner().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
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
    protected boolean isRequestAuthenticated(BecknAsyncTask task, Request request){
        Map<String,String> headers = getPath().getHeaders();

        ServerNode node = InternalNetwork.getRemoteServer(getPath(),true);
        if (node == null){
            String callbackUrl = getGatewayUrl(request.extractAuthorizationParams("X-Gateway-Authorization",headers));
            if (callbackUrl == null) {
                callbackUrl = request.getContext().getBapUri();
            }
            request.getExtendedAttributes().set(BecknExtnAttributes.CALLBACK_URL,callbackUrl);

            if ( Config.instance().getBooleanProperty("beckn.auth.enabled", false)) {
                if (getPath().getHeader("X-Gateway-Authorization") != null) {
                    task.registerSignatureHeaders("X-Gateway-Authorization");
                }
                if (getPath().getHeader("Proxy-Authorization") != null){
                    task.registerSignatureHeaders("Proxy-Authorization");
                }
                if (getPath().getHeader("Authorization") != null) {
                    task.registerSignatureHeaders("Authorization");
                }
                return task.verifySignatures(false);
            }else {
                return true;
            }
        }else {
            request.getExtendedAttributes().set(BecknExtnAttributes.CALLBACK_URL,node.getBaseUrl() +"/" + "beckn_messages");
            request.getExtendedAttributes().set(BecknExtnAttributes.INTERNAL,true);
            return true;
        }
    }


    private <C extends BecknAsyncTask> BecknAsyncTask createTask(Class<C> clazzTask, Request request, Map<String,String> headers, BecknNetwork network){
        try {
            BecknAsyncTask task = null;
            if (clazzTask == null){
                task = new BecknAsyncTask(request,headers){

                    @Override
                    public Request executeInternal() {
                        return null;
                    }
                };
            }else {
                task = clazzTask.getConstructor(Request.class,Map.class).newInstance(request,headers);
            }
            task.setSubscriber(new ProxySubscriberImpl(network.getRetailBppSubscriber()){
                @Override
                public Mq getMq() {
                    return null;
                }
            });
            return task;
        }catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }
    private View act(){
        try {
            BecknNetwork network = BecknNetwork.findByRetailBppUrl(getPath().controllerPath());
            com.venky.swf.plugins.beckn.messaging.Subscriber subscriber =network.getRetailBppSubscriber();
            Class<? extends BecknAsyncTask> clazzTask = subscriber.getTaskClass(getPath().action());
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            request.getContext().setBppId(subscriber.getSubscriberId());
            request.getContext().setBppUri(subscriber.getSubscriberUrl());
            request.getContext().setAction(getPath().action());

            BecknAsyncTask task = createTask(clazzTask,request,getPath().getHeaders(),network);

            if (isRequestAuthenticated(task,request)){
                if (!request.getExtendedAttributes().getBoolean(BecknExtnAttributes.INTERNAL)){
                    List<ServerNode> shards = InternalNetwork.getNodes();
                    if (shards.size() <= 1 ){
                        TaskManager.instance().executeAsync(task, false);
                    }else {
                        submitInternalRequestToShards(shards,request);
                    }
                }else{
                    TaskManager.instance().executeAsync(task, false);
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
        BecknMessage q = Database.getTable(BecknMessage.class).newRecord();
        q.setMessageId(request.getContext().getMessageId());
        BecknMessage message = Database.getTable(BecknMessage.class).getRefreshed(q);
        message.setExpirationTime(System.currentTimeMillis() + request.getContext().getTtl() * 1000L);
        message.setNumPendingResponses(new Bucket(nodes.size()));
        message.setCallBackUri(request.getExtendedAttributes().get(BecknExtnAttributes.CALLBACK_URL));
        message.setRequestPath(getPath().controllerPath());
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
                    new Call<InputStream>().timeOut((int)(request.getContext().getTtl() * 1000L)).url(node.getBaseUrl() + "/bpp/" + request.getContext().getAction()).headers(headers).
                            inputFormat(InputFormat.INPUT_STREAM).
                            input(getPath().getInputStream()).method(HttpMethod.POST).getResponseAsJson();
                } catch (Exception e) {
                    BecknMessage m = Database.getTable(BecknMessage.class).lock(message.getId());
                    m.getNumPendingResponses().decrement();
                    m.save();
                    ServerNode n = Database.getTable(ServerNode.class).get(node.getId());
                    n.setApproved(false);
                    n.save();
                }
            });
        }
        TaskManager.instance().executeAsync(tasks,false);
    }

    @RequireLogin(false)
    public View search() {
        return act();
    }
    @RequireLogin(false)
    public View select(){
        return act();
    }
    @RequireLogin(false)
    public View cancel(){
        return act();
    }

    @RequireLogin(false)
    public View init(){
        return act();
    }
    @RequireLogin(false)
    public View confirm(){
        return act();
    }

    @RequireLogin(false)
    public View status(){
        return act();
    }

    @RequireLogin(false)
    public View update(){
        return act();
    }


    @RequireLogin(false)
    public View track(){
        return act();
    }

    @RequireLogin(false)
    public View rating(){
        return act();
    }

    @RequireLogin(false)
    public View support(){
        return act();
    }

    @RequireLogin(false)
    public View get_cancellation_reasons(){
        return act();
    }

    @RequireLogin(false)
    public View get_return_reasons(){
        return act();
    }

    @RequireLogin(false)
    public View get_rating_categories(){
        return act();
    }
    @RequireLogin(false)
    public View get_feedback_categories(){
        return act();
    }

    @RequireLogin(value = false)
    public View on_subscribe() throws Exception{
        String payload = StringUtil.read(getPath().getInputStream());
        JSONObject object = (JSONObject) JSONValue.parse(payload);


        BecknNetwork network = BecknNetwork.findByRetailBppUrl(getPath().controllerPath());
        if (network == null){
            throw new RuntimeException("Could  not identify network from path");
        }

        JSONObject lookupJSON = new JSONObject();
        lookupJSON.put("subscriber_id",network.getRegistryId());
        lookupJSON.put("domain",BecknUtil.LOCAL_RETAIL);
        lookupJSON.put("type", Subscriber.SUBSCRIBER_TYPE_LOCAL_REGISTRY);
        JSONArray array = BecknPublicKeyFinder.lookup(lookupJSON);
        String signingPublicKey = null;
        String encrPublicKey = null;
        if (array.size() == 1){
            JSONObject registrySubscription = ((JSONObject)array.get(0));
            signingPublicKey = (String)registrySubscription.get("signing_public_key");
            encrPublicKey = (String)registrySubscription.get("encr_public_key");
        }
        if (signingPublicKey == null || encrPublicKey == null){
            throw new RuntimeException("Cannot verify Signature, Could not find registry keys for " + lookupJSON);
        }


        if (!Request.verifySignature(getPath().getHeader("Signature"), payload, signingPublicKey)){
            throw new RuntimeException("Cannot verify Signature");
        }
        com.venky.swf.plugins.beckn.messaging.Subscriber bpp = network.getRetailBppSubscriber();


        PrivateKey privateKey = Crypt.getInstance().getPrivateKey(Request.ENCRYPTION_ALGO,
                CryptoKey.find(bpp.getPubKeyId(),CryptoKey.PURPOSE_ENCRYPTION).getPrivateKey());

        PublicKey registryPublicKey = Request.getEncryptionPublicKey(encrPublicKey);

        KeyAgreement agreement = KeyAgreement.getInstance(Request.ENCRYPTION_ALGO);
        agreement.init(privateKey);
        agreement.doPhase(registryPublicKey,true);

        SecretKey key = agreement.generateSecret("TlsPremasterSecret");

        JSONObject output = new JSONObject();
        output.put("answer", Crypt.getInstance().decrypt((String)object.get("challenge"),"AES",key));

        return new BytesView(getPath(),output.toString().getBytes(),MimeType.APPLICATION_JSON);
    }

}
