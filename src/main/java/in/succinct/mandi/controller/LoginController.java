package in.succinct.mandi.controller;

import com.google.api.client.http.HttpRequest;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.MobileMeta;
import in.succinct.mandi.db.model.ServerNode;

public class LoginController extends Controller {
    public LoginController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View index(){
        if (ObjectUtil.equals(getPath().getRequest().getMethod(),"GET")){
            return html("index");
        }else {
            return super.login();
        }
    }
    @RequireLogin(false)
    public View index_otp(){
        if (getPath().getRequest().getMethod().equalsIgnoreCase(HttpMethod.GET.toString())){
            String phoneNumber = getPath().getRequest().getParameter("phone_number");
            MobileMeta meta = MobileMeta.find(phoneNumber);
            if (meta != null){
                ServerNode node = meta.getServerNode();
                if (node != null && !node.isSelf()){
                    return new RedirectorView(getPath(),node.getBaseUrl()+"/login","index_otp?phone_number=" + phoneNumber);
                }
            }
        }

        return html("index_otp");
    }
    @RequireLogin(false)
    public View admin(){
        return super.login();
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
