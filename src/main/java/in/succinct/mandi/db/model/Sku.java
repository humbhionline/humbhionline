package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;

public interface Sku extends in.succinct.plugins.ecommerce.db.model.inventory.Sku {
    // Only for uploading
    
    @IS_VIRTUAL
    public String getAttachmentUrl();
    public void setAttachmentUrl(String attachmentUrl);

}
