package in.succinct.mandi.agents.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Error;
import in.succinct.beckn.Error.Type;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public abstract class BecknAsyncTask implements Task {
    public Request getRequest() {
        return request;
    }

    private Request request;
    public BecknAsyncTask(){
    }
    public BecknAsyncTask(Request request){
        this.request = request;
    }

    public final void execute(){
        try {
            Request out = executeInternal();
            send(out);
        }catch (Exception ex){
            sendError(ex);
        }
    }
    public abstract Request executeInternal();
    public final void sendError(Throwable th) {
        Error error = new Error();

        StringWriter message = new StringWriter();
        th.printStackTrace(new PrintWriter(message));
        error.setMessage(message.toString());

        error.setCode("CALL-FAILED");
        error.setType(Type.DOMAIN_ERROR);

        Request callBackRequest = new Request();
        callBackRequest.setContext(getRequest().getContext());
        if (!callBackRequest.getContext().getAction().startsWith("on_")){
            callBackRequest.getContext().setAction("on_"+getRequest().getContext().getAction());
        }
        callBackRequest.setError(error);
        Config.instance().getLogger(getClass().getName()).log(Level.WARNING,"Encountered Exception", th);
        send(callBackRequest);
    }

    protected final Map<String, String> getHeaders(Request request) {
        Map<String,String> headers  = new HashMap<>();
        if (getRequest().getExtendedAttributes().getBoolean(BecknExtnAttributes.INTERNAL)){
            ServerNode self = ServerNode.selfNode();
            String token = String.format("%s:%s",self.getClientId(),self.getClientSecret());
            token = String.format("Basic %s", Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
            headers.put("Authorization", token);
        }else if (Config.instance().getBooleanProperty("beckn.auth.enabled", false)) {

            headers.put("Authorization", request.generateAuthorizationHeader(request.getContext().getBppId(),
                    BecknUtil.getCryptoKeyId()));
        }
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        headers.put("Accept", MimeType.APPLICATION_JSON.toString());

        return headers;
    }

    private void send(Request request){
        if (request == null){
            return;
        }
        if (ObjectUtil.isVoid(request.getContext().getAction())){
            request.getContext().setAction("on_"+getRequest().getContext().getAction());
        }
        new Call<JSONObject>().url(getRequest().getExtendedAttributes().get(BecknExtnAttributes.CALLBACK_URL) + "/"+request.getContext().getAction()).
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(request.getInner()).headers(getHeaders(request)).getResponseAsJson();
    }
}
