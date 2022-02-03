package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.blog.rss.Channel;
import in.succinct.mandi.db.model.blog.rss.Post;

public class PostsController extends ModelController<Post> {
    public PostsController(Path path) {
        super(path);
    }

    @Override
    @RequireLogin(value = false)
    public View index() {
        return super.index();
    }
}
