package in.succinct.mandi.agents.beckn;


import com.venky.swf.db.Database;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;

import java.util.Map;

public class Rating extends BecknAsyncTask {

    public Rating(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Context context = getRequest().getContext();
        Message message = getRequest().getMessage();
        in.succinct.beckn.Rating rating = message.getRating();
        in.succinct.mandi.db.model.beckn.Rating persistence = Database.getTable(in.succinct.mandi.db.model.beckn.Rating.class).newRecord();
        persistence.setRatingCategory(rating.getRatingCategory());
        persistence.setObjectId(rating.getId());
        persistence.setValue(rating.getValue());
        persistence = Database.getTable(in.succinct.mandi.db.model.beckn.Rating.class).getRefreshed(persistence);
        persistence.save();


        Request onRating = new Request();
        onRating.setContext(context);
        onRating.getContext().setAction("on_rating");
        onRating.setMessage(new Message());
        onRating.getMessage().set("feedback_ack",false);
        onRating.getMessage().set("rating_ack",persistence.getValue() > 0);
        return onRating;
    }
}
