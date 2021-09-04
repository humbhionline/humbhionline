package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Count;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.ServerNode;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ServerNodesController extends ModelController<ServerNode> {
    public ServerNodesController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View next_node_id(){
        List<Count> counts = new Select("MAX(NODE_ID) AS ID", "COUNT(1) AS COUNT").from(getModelClass()).execute(Count.class);
        Long nextNodeId = 1L;
        if (!counts.isEmpty()){
            Count count = counts.get(0);
            nextNodeId = count.getId();
        }
        nextNodeId += 1;
        return new BytesView(getPath(),nextNodeId.toString().getBytes(StandardCharsets.UTF_8), MimeType.TEXT_PLAIN.toString());
    }


}
