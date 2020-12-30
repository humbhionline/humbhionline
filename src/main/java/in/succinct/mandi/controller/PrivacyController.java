package in.succinct.mandi.controller;

import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.View;

public class PrivacyController  extends TemplatedController {
    public PrivacyController(Path path) {
        super(path);
    }
    public View index(){
        return html("index");
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("privacy");
    }
}
