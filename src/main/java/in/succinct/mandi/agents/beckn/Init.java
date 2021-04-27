package in.succinct.mandi.agents.beckn;


import in.succinct.beckn.Order;
import in.succinct.beckn.Request;

public class Init extends BecknAsyncTask {

    public Init(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        Order order = request.getMessage().getOrder();


    }
}
