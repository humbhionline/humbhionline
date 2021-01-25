package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;

public class ScreenshotsController extends TemplatedController {
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
        return htmlFragment("story");
    }

}
