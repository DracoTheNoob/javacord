package fr.dtn.javacord.commands.slash;

import net.dv8tion.jda.api.interactions.commands.OptionType;

public record Parameter(OptionType type, String name, String description, boolean required, boolean autoComplete, String[] choices) {
}