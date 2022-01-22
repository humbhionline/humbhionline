package in.succinct.mandi.agents.beckn;

import com.venky.swf.plugins.beckn.tasks.BppTask;
import in.succinct.beckn.Request;

import java.util.Map;

public abstract class BecknAsyncTask extends BppTask {
    public BecknAsyncTask( Request request , Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request generateCallBackRequest() {
        return executeInternal();
    }
    public abstract Request executeInternal() ;



}
