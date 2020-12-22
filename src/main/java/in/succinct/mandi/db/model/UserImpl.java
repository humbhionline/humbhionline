package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.table.ModelImpl;

public class UserImpl  extends ModelImpl<User> {
    public UserImpl(){
        super();
    }
    public UserImpl(User user){
        super(user);
    }

    public boolean isPasswordSet() {
        return !ObjectUtil.isVoid(getProxy().getPassword());
    }

    public void setPasswordSet(boolean set){
        //Do nothing.
    }
}
