package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.ProxySubscriberImpl;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.beckn.Subscriber;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.BecknExtnAttributes;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.StringTokenizer;

public class BppController extends Controller {
    public BppController(Path path) {
        super(path);
    }

    public View nack(Request request,String realm){
        Acknowledgement nack = new Acknowledgement(Status.NACK);

        return new BytesView(getPath(),
                new Response(new Acknowledgement(Status.NACK)).getInner().toString().getBytes(StandardCharsets.UTF_8),
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
        return new BytesView(getPath(),new Response(ack).getInner().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
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
                TaskManager.instance().executeAsync(task, false);
                return ack(request);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
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
