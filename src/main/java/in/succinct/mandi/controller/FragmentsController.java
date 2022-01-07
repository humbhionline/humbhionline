package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.views.View;

public class FragmentsController extends Controller {
    public FragmentsController(Path path) {
        super(path);
    }
    @Override
    @RequireLogin(false)
    public View html(String path) {
        return htmlFragment(path);
    }


    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("fragments");
    }

}
