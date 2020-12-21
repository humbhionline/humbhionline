package in.succinct.mandi.controller;

import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.controller.ModelController;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select.ResultFilter;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;

import java.math.BigDecimal;
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
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE","LAT","LNG","DELIVERY_PROVIDED","DELIVERY_RADIUS","DELIVERY_CHARGES"));
        map.put(Attachment.class,Arrays.asList("ID","ATTACHMENT_URL"));
        return map;
    }

    @Override
    protected Map<Class<? extends Model>, List<Class<? extends Model>>> getConsideredChildModels() {
        Map<Class<? extends Model>, List<Class<? extends Model>>> cache =  super.getConsideredChildModels();
        cache.get(Facility.class).add(Attachment.class);
        cache.get(Sku.class).add(Attachment.class);
        return cache;
    }
    protected int getMaxDistance(){
        return Math.max(5,getReflector().getJdbcTypeHelper().getTypeRef(Integer.class).getTypeConverter().
                valueOf(getPath().getFormFields().get("MaxDistance")));
    }

    @Override
    protected ResultFilter<Inventory> getFilter() {
        final ResultFilter<Inventory> superFilter = super.getFilter();

        return record -> {
            boolean pass = true;
            Facility facility = record.getFacility().getRawRecord().getAsProxy(Facility.class);

            pass = pass && facility.isPublished();
            pass = pass && facility.getDistance() < getMaxDistance() ;
            pass = pass && ( record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0
                    || record.getSku().getItem().getRawRecord().getAsProxy(Item.class).isHumBhiOnlineSubscriptionItem() );
            pass = pass && (record.isInfinite() || record.getQuantity() > 0);
            pass = pass &&  superFilter.pass(record);

            return pass;
        };

    }

    @Override
    protected Expression getWhereClause() {
        Expression where = super.getWhereClause();
        int maxDistance = getMaxDistance();
        if (maxDistance <= 10){
            com.venky.swf.db.model.User currentUser = Database.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getCurrentLat() != null) {
                BoundingBox bb = new BoundingBox(new GeoCoordinate(new GeoLocation() {
                    @Override
                    public BigDecimal getLat() {
                        return currentUser.getCurrentLat();
                    }

                    @Override
                    public void setLat(BigDecimal bigDecimal) {
                        currentUser.setCurrentLat(bigDecimal);
                    }

                    @Override
                    public BigDecimal getLng() {
                        return currentUser.getCurrentLng();
                    }

                    @Override
                    public void setLng(BigDecimal bigDecimal) {
                        currentUser.setCurrentLng(bigDecimal);
                    }
                }),2,maxDistance);

                List<Facility> facilities  = bb.find(Facility.class,getMaxListRecords());
                if (!facilities.isEmpty()){
                    where.add(new Expression(getReflector().getPool(),"FACILITY_ID", Operator.IN, DataSecurityFilter.getIds(facilities).toArray()));
                }else {
                    where.add(new Expression(getReflector().getPool(), "ID",Operator.EQ, -1)); //Impossible!!
                }
            }
        }
        return where;
    }
}