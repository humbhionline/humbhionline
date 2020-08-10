package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.View;

public class SellController extends TemplatedController {
    public SellController(Path path) {
        super(path);
    }
    @RequireLogin
    public View index(){
        return html("index",false);
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("sell");
    }
}
