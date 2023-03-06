package rw.session.cmds.completion;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import rw.completion.CompletionMode;
import rw.session.cmds.Cmd;

import java.util.List;

abstract public class GetCtxCompletion extends Cmd {
    public class Return extends Cmd.Return {
        List<Suggestion> suggestions;

        public List<Suggestion> getSuggestions() {
            return this.suggestions;
        }
    }

    String file;
    @Nullable String parent;
    int mode;
    String prompt;

    public GetCtxCompletion(String file, @Nullable String parent, String prompt, CompletionMode mode) {
        this.file = file;
        this.prompt = prompt;
        this.parent = parent;
        this.mode = mode.getValue();
    }
}
