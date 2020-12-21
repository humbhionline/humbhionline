package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import com.venky.swf.routing.Router;
import com.venky.swf.views.View;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ServerController extends Controller {
    public ServerController(Path path) {
        super(path);
    }
    public View restart(){
        String token = getPath().getHeader("Gitlab-Token");
        String expectedToken = Base64.getEncoder().encodeToString(Config.instance().getProperty("gtok","").getBytes(StandardCharsets.UTF_8));
        if (ObjectUtil.equals(token,expectedToken)){
            Router.instance().shutDown();
            Runtime.getRuntime().exit(-1); //Will Force A Restart.
        }else {
            throw new AccessDeniedException();
        }
        return back();
    }
}
