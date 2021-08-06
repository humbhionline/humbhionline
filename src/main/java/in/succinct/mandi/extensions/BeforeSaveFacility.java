package in.succinct.mandi.extensions;

import com.venky.core.math.DoubleUtils;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.plugins.collab.extensions.beforesave.BeforeSaveAddress;
import com.venky.swf.plugins.collab.util.BoundingBox;
import in.succinct.mandi.db.model.Facility;

public class BeforeSaveFacility extends BeforeSaveAddress<Facility> {
    static {
        registerExtension(new BeforeSaveFacility());
    }

    @Override
    protected boolean isOkToSetLocationAsync() {
        return false;
    }

    @Override
    public void beforeSave(Facility model) {
        super.beforeSave(model);
        if (!model.getRawRecord().isFieldDirty("LAT") && !model.getRawRecord().isFieldDirty("LNG") && !model.getRawRecord().isFieldDirty("DELIVERY_RADIUS")
                && model.getMinLat() != null && model.getMaxLat() != null ){
            return;
        }
        if (model.getLat() != null && model.getLng() != null){
            if (DoubleUtils.compareTo(model.getDeliveryRadius(),0)<=0){
                model.setDeliveryRadius(0.0D);
            }
            if (!model.isDeliveryProvided()){
                model.setDeliveryRadius(0.0D);
            }else if (DoubleUtils.equals(model.getDeliveryRadius(),0)){
                model.setDeliveryProvided(false);
            }
            if (model.isDeliveryProvided() && model.getDeliveryRadius() > 0) {
                BoundingBox bb = new BoundingBox(new GeoCoordinate(model), 0, model.getDeliveryRadius());
                model.setMinLat(bb.getMin().getLat());model.setMinLng(bb.getMin().getLng());
                model.setMaxLat(bb.getMax().getLat());model.setMaxLng(bb.getMax().getLng());
            }
        }
    }
}
