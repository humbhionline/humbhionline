package in.succinct.mandi.db.model;

import com.venky.swf.db.table.ModelImpl;

public class MobileMetaImpl extends ModelImpl<MobileMeta> {
    public MobileMetaImpl(MobileMeta meta){
        super(meta);
    }
    public MobileMetaImpl(){
        super();
    }
    public Long getServerNodeId() {
        return getProxy().getTelecomCircle().getServerNodeId();
    }

    public void setServerNodeId(Long id){
        //nothing.
    }
}
