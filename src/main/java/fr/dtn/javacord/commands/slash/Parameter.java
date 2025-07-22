package fr.dtn.javacord.commands.slash;

import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * Class that represents a parameter of a slash command
 *
 * @param type     The datatype of the argument
 * @param name     The displayed label of the parameter in the command
 * @param description The description of what the parameter is used for
 * @param required If the parameter is required
 * @param autoComplete If auto complete is enabled on it
 * @param choices  The different choices that can be entered on the parameter
 */
public record Parameter(OptionType type, String name, String description, boolean required, boolean autoComplete, String[] choices) {
}