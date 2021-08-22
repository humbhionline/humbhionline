package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.routing.Config;

public class ServerNodeImpl extends ModelImpl<ServerNode> {
    public ServerNodeImpl(ServerNode node){
        super(node);
    }
    public Long getPrimaryKeyOffset(){
        ServerNode node = getProxy();
        return getProxy().getReflector().getJdbcTypeHelper().getPrimaryKeyOffset(node.getNodeId());
    }
    public boolean isSelf(){
        return ObjectUtil.equals(Config.instance().getServerBaseUrl(),getProxy().getBaseUrl());
    }
}
