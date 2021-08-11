package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;

public class ScreenshotsController extends Controller {
    public ScreenshotsController(Path path) {
        super(path);
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("screenshots");
    }

    @Override
    @RequireLogin(false)
    public View index() {
        return html("story");
    }

    @Override
    @RequireLogin(false)
    public HtmlView html(String path) {
        return super.html(path);
    }

    @Override
    @RequireLogin(false)
    public HtmlView htmlFragment(String path) {
        return super.htmlFragment(path);
    }
}
