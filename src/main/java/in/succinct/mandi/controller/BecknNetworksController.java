package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.SingleRecordAction;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.beckn.messaging.BppSubscriber;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.beckn.BecknNetwork;

@SuppressWarnings("unused")
public class BecknNetworksController extends ModelController<BecknNetwork> {
    public BecknNetworksController(Path path) {
        super(path);
    }

    @SingleRecordAction(icon = "fas fa-plug")
    public View subscribe(long id){
        BecknNetwork network = Database.getTable(BecknNetwork.class).get(id);
        network.subscribe();
        return IntegrationAdaptor.instance(SWFHttpResponse.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).createStatusResponse(getPath(),null);
    }
}
