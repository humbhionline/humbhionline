package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;

public class LoginController extends TemplatedController {
    public LoginController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View index(){
        return html("index");
    }


    @Override
    public HtmlView html(String path) {
        return html(path,false);
    }


    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("login");
    }
}
