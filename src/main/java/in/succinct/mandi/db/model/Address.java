package in.succinct.mandi.db.model;


import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.model.Model;

@IS_VIRTUAL
public interface Address extends com.venky.swf.plugins.collab.db.model.participants.admin.Address, Model {
    public String getLongName();
    public void setLongName(String name);

}
