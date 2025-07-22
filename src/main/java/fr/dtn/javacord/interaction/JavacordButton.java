package fr.dtn.javacord.interaction;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.UUID;

public class JavacordButton {
    private final UUID id;
    private final ButtonStyle style;
    private final String label;
    private final ButtonExecutor executor;

    public JavacordButton(ButtonStyle style, String label, ButtonExecutor executor) {
        this.id = UUID.randomUUID();
        this.style = style;
        this.label = label;
        this.executor = executor;
    }

    public void execute(Bot bot, ButtonInteractionEvent event) {
        executor.run(bot, event);
    }

    public UUID getId() {
        return id;
    }

    public ButtonStyle getStyle() {
        return style;
    }

    public String getLabel() {
        return label;
    }
}
