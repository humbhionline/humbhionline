package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.views.View;

public class SellController extends Controller {
    public SellController(Path path) {
        super(path);
    }
    @RequireLogin
    public View index(){
        return html("index",false);
    }

    public View template(){
        return load("template.xlsx", MimeType.APPLICATION_XLS.toString(),true);
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory("sell");
    }
}
