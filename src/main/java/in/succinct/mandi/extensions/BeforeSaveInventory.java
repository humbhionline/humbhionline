package in.succinct.mandi.extensions;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Tag;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;

import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BeforeSaveInventory extends BeforeModelSaveExtension<Inventory> {
    static {
        registerExtension(new BeforeSaveInventory());
    }
    @Override
    public void beforeSave(Inventory inventory) {
        Sku sku = inventory.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        if (inventory.getRawRecord().isNewRecord()){
            inventory.setInfinite(false);
        }
        if (item.isItemRestrictedToSingleSeller()){
            List<Sku> skus = item.getSkus();
            List<Long> skuIds = skus.stream().map(s->s.getId()).collect(Collectors.toList());
            ModelReflector<Inventory> ref = ModelReflector.instance(Inventory.class);

            List<Inventory> inventoryList = new Select().from(Inventory.class).
                    where(new Expression(ref.getPool(), Conjunction.AND).
                            add(new Expression(ref.getPool(),"SKU_ID" , Operator.IN, skuIds.toArray())).
                            add(new Expression(ref.getPool(), "FACILITY_ID", Operator.NE, inventory.getFacilityId()))).
                    execute(1);
            if (!inventoryList.isEmpty()){
                throw new RuntimeException("Sku can be sold only from " + inventoryList.get(0).getFacility().getName() );
            }
        }
        if (inventory.isInfinite() || inventory.getQuantity() > 0 ){
            List<AssetCode> assetCodes = new Select().from(AssetCode.class).where(new Expression(ModelReflector.instance(AssetCode.class).getPool(),"CODE",Operator.LK,"99681%")).execute();
            List<Long> deliverySkuIds = new SequenceSet<>();
            for (AssetCode ac : assetCodes){
                List<in.succinct.plugins.ecommerce.db.model.catalog.Item> items = ac.getItems();
                for (in.succinct.plugins.ecommerce.db.model.catalog.Item i :items){
                    deliverySkuIds.addAll(DataSecurityFilter.getIds(i.getSkus()));
                }
            }
            boolean currentSkuIsDeliveryAsset = deliverySkuIds.contains(inventory.getSkuId());

            Expression where = new Expression(inventory.getReflector().getPool(),Conjunction.AND);
            where.add(new Expression(inventory.getReflector().getPool(),"FACILITY_ID",Operator.EQ,inventory.getFacilityId()));
            if (currentSkuIsDeliveryAsset){
                where.add(new Expression(inventory.getReflector().getPool(),"SKU_ID",Operator.NOT_IN,deliverySkuIds.toArray()));
            }else{
                where.add(new Expression(inventory.getReflector().getPool(),"SKU_ID",Operator.IN,deliverySkuIds.toArray()));
            }

            Expression publishedWhere = new Expression(inventory.getReflector().getPool(),Conjunction.OR);
            publishedWhere.add(new Expression(inventory.getReflector().getPool(),"INFINITE",Operator.EQ,true));
            publishedWhere.add(new Expression(inventory.getReflector().getPool(),"QUANTITY",Operator.GT,0));
            where.add(publishedWhere);

            List<Inventory> inventoryList = new Select().from(Inventory.class).where(where).execute(1);

            if (!inventoryList.isEmpty()){
                throw new RuntimeException("Delivery Services and products cannot be Published from the same facility.");
            }

        }

        if (!ObjectUtil.isVoid(inventory.getTags()) &&
                inventory.getRawRecord().isFieldDirty("TAGS")) {
            StringTokenizer tokenizer = new StringTokenizer(inventory.getTags(), ",");
            Set<String> tags = new TreeSet<>();
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                tags.add(token);
            }
            StringBuilder sTag = new StringBuilder();
            for (String tag : tags){
                if (sTag.length() > 0){
                    sTag.append(",");
                }
                sTag.append(tag);
            }
            inventory.setTags(sTag.toString());

            List<Tag> tagList = new Select().from(Tag.class).where(new Expression(ModelReflector.instance(Tag.class).getPool(), "NAME", Operator.IN , tags.toArray())).execute();
            if (tagList.size() < tags.size()){
                List<String> existingTags = new SequenceSet<>();
                for (Tag tag : tagList) {
                    existingTags.add(tag.getName());
                }
                tags.removeAll(existingTags);
                List<Task> tasks = new ArrayList<>();
                for (String tag: tags){
                    tasks.add(new CreateTagTask(tag));
                }
                TaskManager.instance().executeAsync(tasks,false);
            }
        }


    }

    public static class CreateTagTask implements Task {
        String tag;
        public CreateTagTask(String tag){
            this.tag = tag;
        }
        public CreateTagTask(){

        }

        @Override
        public void execute() {
            List<Tag> tags = new Select().from(Tag.class)
                    .where(new Expression(ModelReflector.instance(Tag.class).getPool(),"NAME", Operator.EQ, tag)).execute();
            if (tags.isEmpty()){
                Tag tag  = Database.getTable(Tag.class).newRecord();
                tag.setName(this.tag.trim());
                tag = Database.getTable(Tag.class).getRefreshed(tag);
                tag.save();
            }
        }
    }
}
