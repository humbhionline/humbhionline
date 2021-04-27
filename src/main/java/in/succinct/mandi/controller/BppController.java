package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
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
import in.succinct.mandi.agents.beckn.Track;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BppController extends Controller {
    public BppController(Path path) {
        super(path);
    }

    public View nack(Request request,String realm){
        Acknowledgement nack = new Acknowledgement(Status.NACK);
        //nack.setSignature(Request.generateSignature(request.hash(),request.getPrivateKey(request.getContext().getBppId(),request.getContext().getBppId() +".k1")));

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
        ack.setSignature(Request.generateSignature(request.hash(),request.getPrivateKey(request.getContext().getBppId(),request.getContext().getBppId() +".k1")));
        return new BytesView(getPath(),new Response(request.getContext(),ack).toString().getBytes(StandardCharsets.UTF_8));
    }


    private <C extends BecknAsyncTask> View act(Class<C> clazzTask){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (request.verifySignature("Authorization",getPath().getHeaders())){
                TaskManager.instance().executeAsync(clazzTask.getConstructor(Request.class).newInstance(request));
                return ack(request);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    public View search() {
        return act(Search.class);
    }
    public View select(){
        return act(Select.class);
    }
    public View cancel(){
        return act(Cancel.class);
    }

    public View init(){
        return act(Init.class);
    }
    public View confirm(){
        return act(Confirm.class);
    }

    public View status(){
        return act(in.succinct.mandi.agents.beckn.Status.class);
    }
    public View track(){
        return act(Track.class);
    }
    public View update(){
        return act(Update.class);
    }


    public View rating(){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (request.verifySignature("Authorization",getPath().getHeaders())){
                return ack(request);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    public View support(){
        try {
            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            if (request.verifySignature("Authorization",getPath().getHeaders())){
                return ack(request);
            }else {
                return nack(request,request.getContext().getBapId());
            }
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

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


}
