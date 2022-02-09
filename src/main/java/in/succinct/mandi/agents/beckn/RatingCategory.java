package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.RatingCategories;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.beckn.Rating;

import java.util.Arrays;
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
        callback.setRatingCategories(ratingCategories);

        Arrays.stream(Rating.RATING_CATEGORIES.split(",")).forEach(ratingCategories::add);

        return callback;
    }
}
