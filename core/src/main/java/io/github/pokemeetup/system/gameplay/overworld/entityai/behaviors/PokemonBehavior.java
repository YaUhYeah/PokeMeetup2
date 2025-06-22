package io.github.pokemeetup.system.gameplay.overworld.entityai.behaviors;

public interface PokemonBehavior {
    void execute(float delta);
    boolean canExecute();
    int getPriority();
    String getName();
}
