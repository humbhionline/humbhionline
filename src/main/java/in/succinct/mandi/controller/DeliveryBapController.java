package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.DATA_TYPE;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.reflection.ModelReflector;
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
import in.succinct.beckn.Error;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.integrations.beckn.MessageCallbackUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class DeliveryBapController extends Controller {
    public DeliveryBapController(Path path) {
        super(path);
    }
    public View nack(Request request, String realm){
        Acknowledgement nack = new Acknowledgement(Status.NACK);

        return new BytesView(getPath(),
                new Response(request.getContext(),new Acknowledgement(Status.NACK)).toString().getBytes(StandardCharsets.UTF_8),
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

    private View act(){
        Request request =null ;
        try {
            request = new Request(StringUtil.read(getPath().getInputStream()));
            Map<String, String> params = request.extractAuthorizationParams("Authorization",getPath().getHeaders());

            if (!Config.instance().getBooleanProperty("beckn.auth.enabled", false)  || request.verifySignature(params,false)){
                String messageId = request.getContext().getMessageId();
                //request.getContext().setBppId(params.get("subscriber_id")); //Dunzo bug to be fixed!!
                if (request.getContext().getAction().equals("on_status")){
                    TaskManager.instance().executeAsync(new OnStatus(request),false);
                }else {
                    MessageCallbackUtil.getInstance().registerResponse(messageId, request.getInner());
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
    public static class OnStatus  implements Task {
        Request request;
        public OnStatus(Request request){
            this.request = request;
        }

        @Override
        public void execute() {
            in.succinct.beckn.Order order = this.request.getMessage().getOrder();
            String orderId = order.getId();
            Order myorder = Order.find(orderId);
            if (myorder != null){
                if (order.getState().equals("COMPLETE")){
                    myorder.deliver();
                }
            }
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
        lookupJSON.put("domain","nic2004:55204");
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

        PrivateKey privateKey = Crypt.getInstance().getPrivateKey(Request.ENCRYPTION_ALGO, BecknUtil.getSelfEncryptionKey(network,BecknUtil.LOCAL_DELIVERY).getPrivateKey());
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
