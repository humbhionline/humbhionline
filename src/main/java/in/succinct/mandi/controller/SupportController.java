package in.succinct.mandi.controller;

import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;

public class SupportController extends Controller {
    public SupportController(Path path) {
        super(path);
    }
    @RequireLogin(false)
    public View index(){
        if (getPath().getSessionUserId() == null){
            return new RedirectorView( getPath(),"/dashboard","index#faq" );
        }else {
            return new RedirectorView(getPath(),"/issues","index");
        }
    }
}
