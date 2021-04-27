package in.succinct.mandi.agents.beckn;

import com.venky.swf.plugins.background.core.Task;
import in.succinct.beckn.Request;

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
}
