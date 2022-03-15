package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.PegDownProcessor;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.util.CompanyUtil;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DashboardController extends Controller {
    public DashboardController(Path path) {
        super(path);
    }



    @RequireLogin(false)
    public View index(){
        Facility facility = CompanyUtil.getFacilityForCustomDomain();
        if (facility != null && "GET".equalsIgnoreCase(getPath().getRequest().getMethod())) {
            if (!getPath().getFormFields().containsKey("facility_id")){
                return new RedirectorView(getPath(),"index?facility_id="+facility.getId());
                //White label custom domain
            }
        }

        if (getPath().getSessionUser() != null || !getPath().getFormFields().isEmpty()) {
            return html("index");
        }else {
            return html("index_nologin");
        }
    }

    @RequireLogin(false)
    public View actual_terms(){
        String dir = getTemplateDirectory();
        File file = new File(dir,"html/actual_terms.md");
        try {
            FileInputStream is = new FileInputStream(file);
            String html = new PegDownProcessor().markdownToHtml(StringUtil.read(is));
            return new BytesView(getPath(),html.getBytes(StandardCharsets.UTF_8), MimeType.TEXT_HTML);
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    @RequireLogin(false)
    public View terms_and_conditions(){
        return html("actual_terms");
    }

    @Override
    public View html(String path) {
        return html(path,false);
    }

}
