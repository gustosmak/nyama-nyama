package com.DirtyTeam.sky.NyamaNyama;

import java.util.List;

public final class DesireSelection {

    public final int hungerLevel;
    public final String hungerPhrase;
    public final List<BudgetCategory> frames;
    public final String comment;

    public DesireSelection(int hungerLevel, String hungerPhrase, List<BudgetCategory> frames, String comment) {
        this.hungerLevel = hungerLevel;
        this.hungerPhrase = hungerPhrase;
        this.frames = frames;
        this.comment = comment;
    }
}
