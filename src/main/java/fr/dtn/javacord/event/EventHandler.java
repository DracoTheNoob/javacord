package fr.dtn.javacord.event;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.events.GenericEvent;

public interface EventHandler<T extends GenericEvent> {
    void onEvent(Bot bot, T event);
}
