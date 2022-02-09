package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.FeedbackCategories;
import in.succinct.beckn.RatingCategories;
import in.succinct.beckn.Request;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;

import java.util.Map;

public class FeedbackCategory extends BecknAsyncTask {
    public FeedbackCategory(Request request, Map<String, String> headers) {
        super(request, headers);
    }

    @Override
    public Request executeInternal() {
        Request callback = new Request();
        callback.setContext(getRequest().getContext());
        callback.getContext().setAction("feedback_categories");
        FeedbackCategories feedbackCategories = new FeedbackCategories();
        callback.setFeedbackCategories(feedbackCategories);

        return callback;
    }
}
