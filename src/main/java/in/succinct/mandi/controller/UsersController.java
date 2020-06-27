package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.routing.Config;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;

public class UsersController extends com.venky.swf.plugins.collab.controller.UsersController implements TemplateLoader {
    public UsersController(Path path) {
        super(path);
    }

    @RequireLogin
    public View current(){
        return show(getSessionUser());
    }

    @Override
    @RequireLogin
    public HtmlView html(String path) {
        return html(path,false);
    }

    @Override
    public String getTemplateDirectory() {
        StringBuilder dir = new StringBuilder();
        String templateDirectory  = Config.instance().getProperty("swf.ftl.dir");
        if (!ObjectUtil.isVoid(templateDirectory)){
            dir.append(templateDirectory);
        }
        dir.append("/users");
        return dir.toString();
    }
}
