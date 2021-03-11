package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import in.succinct.mandi.agents.AttachmentResolutionReducer;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;

public class BeforeSaveAttachment  extends BeforeModelSaveExtension<Attachment> {
    static {
        registerExtension(new BeforeSaveAttachment());
    }
    @Override
    public void beforeSave(Attachment model) {
        new AttachmentResolutionReducer().resize(model);
    }
}
