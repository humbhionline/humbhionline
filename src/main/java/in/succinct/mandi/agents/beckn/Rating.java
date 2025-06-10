package in.succinct.mandi.agents.beckn;


import com.venky.swf.db.Database;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.beckn.SellerException;
import in.succinct.beckn.SellerException.InvalidOrder;
import in.succinct.beckn.SellerException.NoDataAvailable;
import in.succinct.beckn.Xinput;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;

import java.util.Map;

public class Rating extends BecknAsyncTask {

    public Rating(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Context context = getRequest().getContext();
        Message message = getRequest().getMessage();
        Order order = Order.find(context.getTransactionId());
        if (order == null){
            throw new InvalidOrder("Order for transaction id not found!");
        }
        for (in.succinct.beckn.Rating rating : message.getRatings()){
            in.succinct.mandi.db.model.beckn.Rating persistence = Database.getTable(in.succinct.mandi.db.model.beckn.Rating.class).newRecord();
            persistence.setRatingCategory(rating.getRatingCategory().name());
            persistence.setObjectId(rating.getId());
            persistence.setTransactionId(context.getTransactionId());
            persistence.setValue(rating.getValue());
            persistence = Database.getTable(in.succinct.mandi.db.model.beckn.Rating.class).getRefreshed(persistence);
            persistence.save();
        }


        Request onRating = new Request();
        onRating.setContext(context);
        onRating.getContext().setAction("on_rating");
        onRating.setMessage(new Message());
        onRating.getMessage().setFeedbackForm(new Xinput()); //No more data neeeded!
        return onRating;
    }
}
