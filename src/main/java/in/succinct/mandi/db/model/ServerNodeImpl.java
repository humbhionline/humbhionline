package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.routing.Config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    public boolean isRegistry() {
        String hboRegistry = Config.instance().getProperty("hbo.registry.url");
        if (ObjectUtil.isVoid(hboRegistry)){
            throw new RuntimeException("Registry url not defined");
        }
        return ObjectUtil.equals(getProxy().getBaseUrl(),hboRegistry);
    }

    public String getAuthorizationHeader(){
        ServerNode node = getProxy();
        return String.format("Basic %s",
                Base64.getEncoder().encodeToString(String.format("%s:%s",node.getClientId(),node.getClientSecret()).getBytes(StandardCharsets.UTF_8)));
    }
}
