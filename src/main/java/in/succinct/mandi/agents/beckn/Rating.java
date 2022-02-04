package in.succinct.mandi.agents.beckn;


import in.succinct.beckn.Request;

import java.util.Map;

public class Rating extends BecknAsyncTask {

    public Rating(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        //TODO allow cancellations but not modification of Product.
        return null;
    }
}
