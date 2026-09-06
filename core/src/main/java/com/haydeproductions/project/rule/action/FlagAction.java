package com.haydeproductions.project.rule.action;

import com.haydeproductions.project.state.FileState;

public final class FlagAction implements Action {

    @Override
    public ActionPhase getPhase() {
        return ActionPhase.FLAG;
    }

    @Override
    public void execute(ActionContext context) {
        context.getStateRegistry().setState(
                context.getFile().getPath(),
                FileState.FLAGGED
        );
    }
}