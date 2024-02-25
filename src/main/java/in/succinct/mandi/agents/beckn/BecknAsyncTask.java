package in.succinct.mandi.agents.beckn;

import com.venky.swf.plugins.beckn.tasks.BppTask;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.beckn.BecknNetwork;

import java.util.Map;

public abstract class BecknAsyncTask extends BppTask {
    public BecknAsyncTask( Request request , Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request generateCallBackRequest() {
        Request response = new Request();
        response.setObjectCreator(getNetwork().getNetworkAdaptor().getObjectCreator(getRequest().getContext().getDomain()));
        response.update(executeInternal());
        return response;
    }
    public abstract Request executeInternal() ;


    BecknNetwork network ;
    public void setNetwork(BecknNetwork network) {
        this.network = network;
    }

    public BecknNetwork getNetwork() {
        return network;
    }
}
