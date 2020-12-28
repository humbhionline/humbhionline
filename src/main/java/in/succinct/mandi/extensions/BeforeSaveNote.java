package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.bugs.db.model.Note;
import in.succinct.mandi.agents.AttachmentResolutionReducer;
import in.succinct.mandi.agents.NoteResolutionReducer;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;

public class BeforeSaveNote extends BeforeModelSaveExtension<Note> {
    static {
        registerExtension(new BeforeSaveNote());
    }
    @Override
    public void beforeSave(Note model) {
        new NoteResolutionReducer().resize(model);
    }
}
