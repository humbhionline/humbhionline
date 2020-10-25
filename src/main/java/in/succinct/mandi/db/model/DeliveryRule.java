package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.model.Model;

public interface DeliveryRule extends Model {
    public Long getFacilityId();
    public void setFacilityId(Long id);
    public Facility getFacility();

    public int getFromKm();
    public void setFromKm(int km);

    public int getToKm();
    public void setToKm(int km);


    @COLUMN_DEF(StandardDefault.ZERO)
    public double getAdditionalDeliveryCharges();
    public void setAdditionalDeliveryCharges(double deliveryCharges);

}
