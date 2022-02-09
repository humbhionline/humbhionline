package in.succinct.mandi.agents.beckn;


import com.venky.swf.routing.Config;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;

import java.util.Map;

public class Support extends BecknAsyncTask {

    public Support(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        String refId = getRequest().getMessage().get("ref_id");
        String sEntity = refId.substring(refId.lastIndexOf(".")+1);

        Entity entity = Entity.valueOf(sEntity);
        String entityId = BecknUtil.getLocalUniqueId(refId,entity);

        Request onSupport = new Request();
        onSupport.setContext(getRequest().getContext());
        onSupport.getContext().setAction("on_support");

        onSupport.setMessage(new Message());
        onSupport.getMessage().setEmail("support@"+ Config.instance().getHostName());
        return onSupport;
    }
}
