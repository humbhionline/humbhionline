package in.succinct.mandi.agents.beckn;


import in.succinct.beckn.Request;

public class Update extends BecknAsyncTask {

    public Update(Request request){
        super(request);
    }
    @Override
    public Request executeInternal() {
        //TODO allow cancellations but not modification of Product.
        return null;
    }
}
