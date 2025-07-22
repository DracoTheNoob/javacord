package fr.dtn.javacord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;

public class EmbedUtils {
    public static MessageEmbed create(String title, String description, Color color) {
        return new EmbedBuilder().setTitle(title).setDescription(description).setColor(color).build();
    }

    public static MessageEmbed createError(String description) {
        return create("Error", description, Color.red);
    }
}
