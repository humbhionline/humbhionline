package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.View;

public class BuyController extends TemplatedController {
    public BuyController(Path path) {
        super(path);
    }
    @RequireLogin(false)
    public View index(){
        return html("index",false);
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("buy");
    }
}
