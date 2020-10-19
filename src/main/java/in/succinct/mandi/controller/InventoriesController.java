package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import org.apache.lucene.search.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InventoriesController extends ModelController<Inventory> {
    public InventoriesController(Path path) {
        super(path);
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE"));
        return map;
    }

    @Override
    protected View search(String strQuery, int maxRecords) {
        if (!ObjectUtil.isVoid(strQuery)) {
            if (!getFormFields().containsKey("q")) {
                getFormFields().put("q", strQuery);
            }
            LuceneIndexer indexer = LuceneIndexer.instance(getModelClass());
            Query q = indexer.constructQuery(strQuery);

            List<Long> ids = indexer.findIds(q, Select.MAX_RECORDS_ALL_RECORDS);
            if (!ids.isEmpty()) {
                Select sel = new Select().from(getModelClass()).where(new Expression(getReflector().getPool(), Conjunction.AND)
                        .add(Expression.createExpression(getReflector().getPool(), "ID", Operator.IN, ids.toArray()))
                        .add(getPath().getWhereClause())).orderBy(getReflector().getOrderBy());
                List<Inventory> records = sel.execute(getModelClass(), maxRecords, new DefaultModelFilter<Inventory>(getModelClass()){
                    @Override
                    public boolean pass(Inventory record) {
                        return (
                                    ( record.getFacility().getRawRecord().getAsProxy(Facility.class).getDistance() < 20  &&
                                        record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0 )
                                || record.getSku().getItem().getRawRecord().getAsProxy(Item.class).isHumBhiOnlineSubscriptionItem() ) &&
                                (record.isInfinite() || record.getQuantity() > 0 )
                                && super.pass(record);
                    }
                });
                return list(records, maxRecords == 0 || records.size() < maxRecords);
            } else {
                return list(new ArrayList<>(), true);
            }
        }
        return list(maxRecords);
    }
}