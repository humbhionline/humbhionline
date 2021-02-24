package in.succinct.mandi.controller;

import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.views.View;
import org.json.simple.JSONObject;

public class WefastController extends TemplatedController {
    public WefastController(Path path) {
        super(path);
    }


    public View record_status() {
        return IntegrationAdaptor.instance(SWFHttpResponse.class, JSONObject.class).createStatusResponse(getPath(),null);
    }
}
