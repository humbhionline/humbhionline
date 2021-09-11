package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.background.core.TaskManager;
import in.succinct.mandi.db.model.beckn.BecknMessage;
import in.succinct.mandi.db.model.beckn.BecknMessage.ResponseConsolidator;

public class BeforeSaveBecknMessage extends BeforeModelSaveExtension<BecknMessage> {
    static {
        registerExtension(new BeforeSaveBecknMessage());
    }
    @Override
    public void beforeSave(BecknMessage message) {
        if (message.getNumPendingResponses().intValue() == 0 && message.getRawRecord().isFieldDirty("NUM_PENDING_RESPONSES") && message.getRawRecord().getOldValue("NUM_PENDING_RESPONSES") != null){
            ResponseConsolidator responseConsolidator = message.getConsolidator();
            if (responseConsolidator != null ) {
                TaskManager.instance().executeAsync(responseConsolidator,false);
            }
        }
    }
}
