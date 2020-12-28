package in.succinct.mandi.controller;

import com.venky.extension.Registry;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.extensions.FacilityParticipantExtension;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FacilitiesController extends ModelController<Facility> {
    public FacilitiesController(Path path) {
        super(path);
    }

    /* Is permission controlled */
    @RequireLogin
    public View publish(long id){
        Facility f = Database.getTable(Facility.class).get(id);
        f.publish();
        return show(f);
    }

    @RequireLogin
    public View unpublish(long id){
        Facility f = Database.getTable(Facility.class).get(id);
        f.unpublish();
        return show(f);
    }

    @RequireLogin
    public View mine(){
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);
        List<Facility> facilityList = new Select().from(Facility.class).where(new Expression(getReflector().getPool(),"ID", Operator.IN,user.getOperatingFacilityIds().toArray())).execute();
        return list(facilityList,true);
    }


    /* Keep in sync with apicontroller*/
    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();

        map.put(Inventory.class, ModelReflector.instance(Inventory.class).getFields());
        List<String> itemFields = ModelReflector.instance(Item.class).getUniqueFields();
        itemFields.add("ASSET_CODE_ID");
        itemFields.add("ID");
        map.put(Item.class, itemFields);

        List<String> skuFields = ModelReflector.instance(Sku.class).getUniqueFields();
        skuFields.add("MAX_RETAIL_PRICE");
        skuFields.add("TAX_RATE");
        skuFields.add("ID");

        map.put(Sku.class,skuFields);

        map.put(AssetCode.class, Arrays.asList("CODE","LONG_DESCRIPTION","GST_PCT"));

        map.put(User.class,ModelReflector.instance(User.class).getUniqueFields());
        map.get(User.class).addAll(Arrays.asList("ID","NAME_AS_IN_BANK_ACCOUNT","VIRTUAL_PAYMENT_ADDRESS"));

        map.put(Attachment.class,Arrays.asList("ID","ATTACHMENT_URL"));
        return map;
    }

    @Override
    protected Map<Class<? extends Model>, List<Class<? extends Model>>> getConsideredChildModels() {
        Map<Class<? extends Model>,List<Class<? extends Model>>> consideredModels = super.getConsideredChildModels();
        consideredModels.get(Sku.class).add(Attachment.class);
        return consideredModels;
    }
}
