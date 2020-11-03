package in.succinct.mandi.db.model;

import java.util.List;

public interface Sku extends in.succinct.plugins.ecommerce.db.model.inventory.Sku {
    List<Attachment> getAttachments();
}
