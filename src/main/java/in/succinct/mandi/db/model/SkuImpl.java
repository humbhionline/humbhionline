package in.succinct.mandi.db.model;

import com.venky.swf.db.table.ModelImpl;

public class SkuImpl extends ModelImpl<Sku> {
    public SkuImpl(Sku sku){
        super(sku);
    }

    /**
     * Used for importing
     */
    String attachmentUrl;
    public String getAttachmentUrl(){
        return attachmentUrl;
    }
    public void setAttachmentUrl(String attachmentUrl){
        this.attachmentUrl = attachmentUrl;
    }
}
