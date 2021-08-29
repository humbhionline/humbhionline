package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.ForwardedView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.ServerNode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class NetworkController extends Controller {
    public NetworkController(Path path) {
        super(path);
    }

    private View collectResponses(String api){
        JSONObject consolidated = null;
        List<ServerNode> nodes  = new Select().from(ServerNode.class).execute();
        if (nodes.size() < 2){
            return new ForwardedView(getPath(), "", api);
        }
        if (getPath().getProtocol() != MimeType.APPLICATION_JSON){
            throw new RuntimeException("Only json apis can be collected");
        }
        JSONObject input = null ;
        try {
            input = (JSONObject) JSONValue.parse(new InputStreamReader(getPath().getInputStream()));
        }catch (IOException ex){
            throw new RuntimeException(ex);
        }
        for (ServerNode node :nodes ){
            String token = String.format("%s:%s",node.getClientId(),node.getClientSecret());
            String auth = String.format("Basic %s", Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));

            JSONObject output = new Call<JSONObject>().url(node.getBaseUrl() +"/" + api).inputFormat(InputFormat.JSON)
                    .input(input).headers(getPath().getHeaders()).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",auth)
                            method(HttpMethod.POST).getResponseAsJson();
            if (output != null){
                if (consolidated == null){
                    consolidated = output;
                }else {
                    merge(consolidated,output);
                }
            }
        }
        if (consolidated == null){
            return null;
        }else {
            return new BytesView(getPath(), consolidated.toJSONString().getBytes(StandardCharsets.UTF_8), MimeType.APPLICATION_JSON);
        }
    }
    private void merge(Object consolidated, Object out){
        if (!ObjectUtil.equals(consolidated,out)){
            throw new RuntimeException("Cannot consolidate");
        }
    }
    private void merge(JSONArray consolidated, JSONArray out){
        consolidated.addAll(out);
    }
    private void merge(JSONObject consolidated, JSONObject out){
        for (Object k : out.keySet()){
            if (!consolidated.containsKey(k)){
                consolidated.put(k,out.get(k));
            }else{
                merge(consolidated.get(k),out.get(k));
            }
        }
    }

    public View call(String api){
        return collectResponses(api);
    }

}
