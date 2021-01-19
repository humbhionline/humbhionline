package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import in.succinct.mandi.db.model.Order;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.io.IOException;

public class BeforeSaveOrderLine extends BeforeModelSaveExtension<OrderLine> {
    static {
        registerExtension(new BeforeSaveOrderLine());
    }
    @Override
    public void beforeSave(OrderLine model) {
        try {
            //Reindex order for virtual fields.
            LuceneIndexer.instance(Order.class).updateDocument(model.getOrder().getRawRecord());
        }catch (IOException ex){
            throw new RuntimeException(ex);
        }
    }
}
