package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.Rating;
import in.succinct.beckn.RatingCategories;
import in.succinct.beckn.Request;

import java.util.Map;

public class RatingCategory extends BecknAsyncTask {
    public RatingCategory(Request request, Map<String, String> headers) {
        super(request, headers);
    }

    @Override
    public Request executeInternal() {
        Request callback = new Request();
        callback.setContext(getRequest().getContext());
        callback.getContext().setAction("rating_categories");
        RatingCategories ratingCategories = new RatingCategories();
        for (Rating.RatingCategory value : Rating.RatingCategory.values()) {
            ratingCategories.add(value);
        }
        callback.setRatingCategories(ratingCategories);
        return callback;
    }
}
