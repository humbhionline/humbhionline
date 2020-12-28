package in.succinct.mandi.agents;

import com.venky.swf.plugins.attachment.db.model.Attachment;

public class AttachmentResolutionReducer  extends ResolutionReducer<Attachment> {
    @Override
    public String getAgentName() {
        return ATTACHMENT_RESOLUTION_REDUCER;
    }
    public static final String ATTACHMENT_RESOLUTION_REDUCER = "ATTACHMENT_RESOLUTION_REDUCER";

    protected String getImageFieldName(){
        return "ATTACHMENT";
    }

}
