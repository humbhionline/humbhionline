package in.succinct.mandi.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
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
import in.succinct.mandi.agents.beckn.Cancel;
import in.succinct.mandi.agents.beckn.Confirm;
import in.succinct.mandi.agents.beckn.Init;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.agents.beckn.Select;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.Cipher;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

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
        ack.setSignature(Request.generateSignature(request.hash(),request.getPrivateKey(request.getContext().getBppId(),request.getContext().getBppId() +".k1")));
        return new BytesView(getPath(),new Response(request.getContext(),ack).getInner().toString().getBytes(StandardCharsets.UTF_8));
    }


    private <C extends BecknAsyncTask> View act(Class<C> clazzTask){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (request.verifySignature("Authorization",getPath().getHeaders())){
                if (clazzTask != null){
                    TaskManager.instance().executeAsync(clazzTask.getConstructor(Request.class).newInstance(request),false);
                }
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
    public View track(){
        return act(null);
    }
    @RequireLogin(false)
    public View update(){
        return act(Update.class);
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
            if (request.verifySignature("Authorization",getPath().getHeaders())){
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
            if (request.verifySignature("Authorization",getPath().getHeaders())){
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
