package in.succinct.mandi.extensions;

import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.extensions.AfterModelSaveExtension;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.io.ModelWriter;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.TaskManager;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.util.InternalNetwork;
import org.json.simple.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;

public class AfterSaveServerNode extends AfterModelSaveExtension<ServerNode> {
    static {
        registerExtension(new AfterSaveServerNode());
    }
    @Override
    public void afterSave(ServerNode node) {
        JSONObject obj= new JSONObject();
        obj.put("ServerNode",new JSONObject());

        if (!InternalNetwork.isCurrentServerRegistry() && node.isSelf() && !node.isApproved()){
            ModelWriter<ServerNode,JSONObject> writer = ModelIOFactory.getWriter(ServerNode.class,
                    FormatHelper.getFormatClass(MimeType.APPLICATION_JSON));
            writer.write(node,(JSONObject) obj.get("ServerNode"),node.getReflector().getVisibleFields(new ArrayList<>()));
            TaskManager.instance().executeAsync(()-> {
                InputStream in = new Call<JSONObject>().url(InternalNetwork.getRegistryUrl() + "/server_nodes/register").input(obj).method(HttpMethod.POST).
                        inputFormat(InputFormat.JSON).header("content-type", MimeType.APPLICATION_JSON.toString()).getResponseStream();
            },false);
        }

    }
}
