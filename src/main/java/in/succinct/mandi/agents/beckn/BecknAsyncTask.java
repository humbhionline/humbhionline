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
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.Request;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public abstract class BecknAsyncTask implements Task {
    public Request getRequest() {
        return request;
    }

    private Request request;
    public BecknAsyncTask(Request request){
        this.request = request;
    }
    public BecknAsyncTask(){

    }

    public void execute(){
        try {
            executeInternal();
        }catch (Exception ex){
            sendError(ex);
        }
    }
    public abstract void executeInternal();
    public final void sendError(Throwable th) {
        Error error = new Error();
        error.setMessage(th.getMessage());
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
        if (Config.instance().getBooleanProperty("beckn.auth.enabled", false)) {
            headers.put("Authorization", request.generateAuthorizationHeader(request.getContext().getBppId(),
                    request.getContext().getBppId() + ".k1"));
        }
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        headers.put("Accept", MimeType.APPLICATION_JSON.toString());

        return headers;
    }

    protected final void send(Request request){
        if (ObjectUtil.isVoid(request.getContext().getAction())){
            request.getContext().setAction("on_"+getRequest().getContext().getAction());
        }
        new Call<JSONObject>().url(getRequest().getCallBackUri() + "/"+request.getContext().getAction()).
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(request.getInner()).headers(getHeaders(request)).getResponseAsJson();
    }
}
