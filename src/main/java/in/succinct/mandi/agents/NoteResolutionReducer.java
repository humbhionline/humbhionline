package in.succinct.mandi.agents;

import com.venky.swf.plugins.bugs.db.model.Note;

public class NoteResolutionReducer extends ResolutionReducer<Note> {
    @Override
    public String getAgentName() {
        return NOTE_RESOLUTION_REDUCER;
    }
    public static final String NOTE_RESOLUTION_REDUCER = "NOTE_RESOLUTION_REDUCER";

    protected String getImageFieldName(){
        return "ATTACHMENT";
    }

}
