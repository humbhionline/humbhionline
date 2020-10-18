package in.succinct.mandi.db.model;

import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.User;
import com.venky.swf.db.table.ModelImpl;

import java.math.BigDecimal;

public class FacilityImpl extends ModelImpl<Facility> {
    public FacilityImpl() {
    }

    public FacilityImpl(Facility proxy) {
        super(proxy);
    }

    public Facility getSelfFacility() {
        return getProxy();
    }
    public void verify(){
        Facility f = getProxy();
        f.setVerified(true);
        f.setVerifiedById(Database.getInstance().getCurrentUser().getId());
        f.save();
    }

    public Double getDistance() {
        Facility facility = getProxy();
        double distance;
        if (facility.getLat() == null ){
            distance = 0.0;
        }else {
            User currentUser = Database.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getCurrentLat() != null) {
                distance = new GeoCoordinate(facility).distanceTo(new GeoCoordinate(new GeoLocation() {
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
                }));
            } else {
                distance = 0.0D;
            }
        }
        return distance;

    }
    public void setDistance(Double distance){

    }
}
