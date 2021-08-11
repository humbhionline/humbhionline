package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.views.View;

public class DownloadController extends Controller {
    public DownloadController(Path path) {
        super(path);
    }

    @Override
    @RequireLogin(false)
    public View index() {
        return html("index");
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("download");
    }
}
