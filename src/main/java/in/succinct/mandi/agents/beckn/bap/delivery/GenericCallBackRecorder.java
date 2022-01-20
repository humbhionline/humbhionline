package in.succinct.mandi.agents.beckn.bap.delivery;

import com.venky.swf.plugins.beckn.tasks.BecknTask;
import in.succinct.beckn.Request;
import in.succinct.mandi.integrations.beckn.MessageCallbackUtil;

import java.util.Map;

public class GenericCallBackRecorder extends BecknTask {
    public GenericCallBackRecorder(Request request, Map<String,String> headers){
        super(request,headers);
    }

    @Override
    public void execute() {
        MessageCallbackUtil.getInstance().registerResponse(getRequest().getContext().getMessageId(),
                getRequest().getInner());
    }
}
