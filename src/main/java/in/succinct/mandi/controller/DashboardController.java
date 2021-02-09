package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;
import org.pegdown.PegDownProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class DashboardController extends TemplatedController {
    public DashboardController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View index(){
        if (getPath().getSessionUser() != null || !getPath().getFormFields().isEmpty()) {
            return html("index");
        }else {
            return html("index_nologin");
        }
    }

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
