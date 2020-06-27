package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;

public class DashboardController extends TemplatedController {
    public DashboardController(Path path) {
        super(path);
    }
    @RequireLogin
    public View index(){
        return html("index");
    }


    @Override
    public HtmlView html(String path) {
        return html(path,false);
    }

    
    @Override
    public String getTemplateDirectory() {
        StringBuilder dir = new StringBuilder();
        String templateDirectory  = super.getTemplateDirectory() ;
        if (!ObjectUtil.isVoid(templateDirectory)){
            dir.append(templateDirectory);
        }
        dir.append("/dashboard");
        return dir.toString();
    }
}
