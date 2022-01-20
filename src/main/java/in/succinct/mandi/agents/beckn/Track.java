package in.succinct.mandi.agents.beckn;


import in.succinct.beckn.Request;

import java.util.Map;

public class Track extends BecknAsyncTask {

    public Track(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        return null;
    }
}
