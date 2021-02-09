package in.succinct.mandi.db.model;

import com.venky.geo.GeoLocation;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;

public interface UserLocation extends GeoLocation, Model {
    @UNIQUE_KEY
    public Long getUserId();
    public void setUserId(Long id);
    public User getUser();

}
