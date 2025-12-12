package hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Scene {
    private final String name;
    private final List<SceneAction> actions = new ArrayList<>();

    public Scene(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void addAction(SceneAction action) {
        actions.add(action);
    }

    public List<SceneAction> getActions() {
        return Collections.unmodifiableList(actions);
    }
}