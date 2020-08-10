package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.path.Path;
import com.venky.swf.views.View;
import com.venky.swf.views.model.FileUploadView;
import in.succinct.mandi.util.GSTParser;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.Map;

public class AssetCodesController extends ModelController<AssetCode> {
    public AssetCodesController(Path path) {
        super(path);
    }

    public View load() throws Exception{
        HttpServletRequest request = getPath().getRequest();

        if (request.getMethod().equalsIgnoreCase("GET")) {
            return dashboard(new FileUploadView(getPath()));
        } else {
            Map<String, Object> formFields = getFormFields();
            if (!formFields.isEmpty()) {
                InputStream in = (InputStream) formFields.get("datafile");
                if (in == null) {
                    throw new RuntimeException("Nothing uploaded!");
                }
                new GSTParser().parse(in);
            }
            return back();
        }
    }
}
