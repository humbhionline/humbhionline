package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.AfterModelSaveExtension;
import in.succinct.mandi.db.model.Sku;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;

public class AfterSaveSku extends AfterModelSaveExtension<Sku> {
    static {
        registerExtension(new AfterSaveSku());
    }
    @Override
    public void afterSave(Sku model) {
        if (model.getAttachmentUrl() != null){
            Attachment attachment = Database.getTable(Attachment.class).newRecord();
            attachment.setUploadUrl(model.getAttachmentUrl());
            attachment.setSkuId(model.getId());
            attachment.save();
        }
    }
}
