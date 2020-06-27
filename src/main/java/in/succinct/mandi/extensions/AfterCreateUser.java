package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.AfterModelCreateExtension;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.routing.Config;
import in.succinct.plugins.ecommerce.db.model.participation.User;

public class AfterCreateUser extends AfterModelCreateExtension<User> {
    static{
        registerExtension(new AfterCreateUser());
    }
    @Override
    public void afterCreate(User model) {
        if (model.getId() > 1 ){
            Role  userRole = Role.getRole("USER");
            if (userRole != null){
                UserRole ur = Database.getTable(UserRole.class).newRecord();
                ur.setUserId(model.getId());
                ur.setRoleId(userRole.getId());
                ur.save();
            }
        }
        UserPhone userPhone = Database.getTable(UserPhone.class).newRecord();
        userPhone.setPhoneNumber(Config.instance().getProperty(model.getName()+".phone_number",
                model.getName()));
        userPhone.setUserId(model.getId());
        userPhone.save();

    }

}
