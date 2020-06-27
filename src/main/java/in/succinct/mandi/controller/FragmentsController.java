package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.HtmlView;

public class FragmentsController extends TemplatedController {
    public FragmentsController(Path path) {
        super(path);
    }
    @Override
    @RequireLogin(false)
    public HtmlView html(String path) {
        return htmlFragment(path,null);
    }


    @Override
    public String getTemplateDirectory() {
        StringBuilder dir = new StringBuilder();
        String templateDirectory  = super.getTemplateDirectory() ;
        if (!ObjectUtil.isVoid(templateDirectory)){
            dir.append(templateDirectory);
        }
        dir.append("/fragments");
        return dir.toString();
    }

}
