package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import com.venky.swf.path._IPath;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.UserLocation;


import java.util.List;

public class LocationLogger implements Extension {

    static {
        Registry.instance().registerExtension(_IPath.USER_LOCATION_UPDATED_EXTENSION, new LocationLogger());
    }
    @Override
    public void invoke(Object... objects) {
        Path path = (Path)objects[0];
        User user = ((Model)objects[1]).getRawRecord().getAsProxy(User.class);
        TaskManager.instance().executeAsync(new LastUserLocationLogger(user),false);
    }

    public static class LastUserLocationLogger implements Task {
        User user = null;
        public LastUserLocationLogger(User user){
            this.user = user;
        }
        public LastUserLocationLogger(){

        }

        @Override
        public void execute() {
            if (this.user == null ) {
                return;
            }
            List<UserLocation> locationList = user.getUserLocations();
            UserLocation location  = null;
            if (locationList.isEmpty()){
                location = Database.getTable(UserLocation.class).newRecord();
                location.setUserId(user.getId());
            }else {
                location = locationList.get(0);
            }
            if (!user.getReflector().isVoid(user.getCurrentLat())
                    && !user.getReflector().isVoid(user.getCurrentLng())){
                location.setLat(user.getCurrentLat());
                location.setLng(user.getCurrentLng());
                location.save();
            }
        }
    }


}
