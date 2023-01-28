package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.ProxySubscriberImpl;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.beckn.tasks.BecknTask;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Error;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
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
import java.util.logging.Level;

public class DeliveryBapController extends Controller {
    public DeliveryBapController(Path path) {
        super(path);
    }
    public View nack(Request request, String realm){
        Acknowledgement nack = new Acknowledgement(Status.NACK);

        return new BytesView(getPath(),
                new Response(new Acknowledgement(Status.NACK)).toString().getBytes(StandardCharsets.UTF_8),
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
        return new BytesView(getPath(),new Response(    ack).getInner().toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
    }

    private View act(){
        Request request =null ;
        try {
            String action = getPath().action();
            request = new Request(StringUtil.read(getPath().getInputStream()));
            BecknNetwork network = BecknNetwork.findByDeliveryBapUrl(getPath().controllerPath());
            Subscriber subscriber = new ProxySubscriberImpl(network.getDeliveryBapSubscriber()){
                @Override
                public Mq getMq() {
                    return null; //Respond vid http
                }
            };
            BecknTask task = subscriber.getTaskClass(action).getConstructor(Request.class,Map.class).newInstance(request,getPath().getHeaders());
            task.setSubscriber(subscriber);
            if (Config.instance().getBooleanProperty("beckn.auth.enabled", false)) {
                task.registerSignatureHeaders("Authorization");
            }
            if (task.verifySignatures(false)){
                if (task.async()){
                    TaskManager.instance().executeAsync(task,false);
                }else {
                    TaskManager.instance().execute(task);
                }
                return ack(request);
            }else {
                return nack(request,request.getContext().getBppId());
            }
        }catch (Exception ex){
            if (request == null){
                throw new RuntimeException(ex);
            }
            Request response  = new Request();
            Error error = new Error();
            response.setContext(request.getContext());
            response.setError(error);
            error.setCode(ex.getMessage());
            error.setMessage(ex.getMessage());
            return new BytesView(getPath(),response.toString().getBytes(StandardCharsets.UTF_8));
        }finally {
            Config.instance().getLogger(getClass().getName()).log(Level.INFO,"Api input " + request.toString());
        }
    }

    @RequireLogin(false)
    public View on_search(){
        return act();
    }
    @RequireLogin(false)
    public View on_select(){
        return act();
    }
    @RequireLogin(false)
    public View on_init(){
        return act();
    }

    @RequireLogin(false)
    public View on_confirm(){
        return act();
    }

    @RequireLogin(false)
    public View on_status(){
        return act();
    }
    @RequireLogin(false)
    public View on_track(){
        return act();
    }
    @RequireLogin(false)
    public View on_cancel(){
        return act();
    }
    @RequireLogin(false)
    public View on_update(){
        return act();
    }


    @RequireLogin(false)
    public View on_rating(){
        return act();
    }

    @RequireLogin(false)
    public View on_support(){
        return act();
    }

    @RequireLogin(value = false)
    @SuppressWarnings("unchecked")
    public View on_subscribe() throws Exception{
        String payload = StringUtil.read(getPath().getInputStream());
        JSONObject object = (JSONObject) JSONValue.parse(payload);

        JSONObject lookupJSON = new JSONObject();
        BecknNetwork network = BecknNetwork.findByDeliveryBapUrl(getPath().controllerPath());
        if (network == null){
            throw new RuntimeException("Could  not identify network from path");
        }

        lookupJSON.put("subscriber_id", network.getRegistryId());
        lookupJSON.put("domain",BecknUtil.LOCAL_DELIVERY);
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

        Subscriber delivery_bap = network.getDeliveryBapSubscriber();

        PrivateKey privateKey = Crypt.getInstance().getPrivateKey(Request.ENCRYPTION_ALGO,
                CryptoKey.find(delivery_bap.getPubKeyId(),CryptoKey.PURPOSE_ENCRYPTION).getPrivateKey());

        PublicKey registryPublicKey = Request.getEncryptionPublicKey(encrPublicKey);

        KeyAgreement agreement = KeyAgreement.getInstance(Request.ENCRYPTION_ALGO);
        agreement.init(privateKey);
        agreement.doPhase(registryPublicKey,true);

        SecretKey key = agreement.generateSecret("TlsPremasterSecret");

        JSONObject output = new JSONObject();
        output.put("answer", Crypt.getInstance().decrypt((String)object.get("challenge"),"AES",key));

        return new BytesView(getPath(),output.toString().getBytes());
    }
}
