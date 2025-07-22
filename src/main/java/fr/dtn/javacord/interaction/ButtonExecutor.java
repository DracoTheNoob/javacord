package fr.dtn.javacord.interaction;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public interface ButtonExecutor {
    void run(Bot bot, ButtonInteractionEvent event);
}
