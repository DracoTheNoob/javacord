# JavaCord

Javacord is a library aiming at simplifying the creation of Discord bots using JDA.

The library is based on several dependencies.
All those dependencies are present in the [build.gradle](build.gradle) file of the project.

## Setup

First, you need to create an application using the [Discord Developer Portal](https://discord.com/developers/applications).

## Gradle implementation

In your build.gradle file, add JitPack to the repositories :

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

You must as well add some dependencies :

```gradle
dependencies {
    implementation 'net.dv8tion:JDA:5.0.0-beta.10'
    implementation 'com.github.DracoTheNoob:javacord:master-SNAPSHOT'
}
```

Then, you will need to create a configuration directory for the bot.
There are two ways to do it : manually, or using Javacord#setup function.

### Manual setup

You need to create an empty directory, which will contain every configuration file of the bot.
We will refer to this directory as the __bot directory__.

In your bot directory, you will create a file called 'config.toml', and fill it based on the following file :

```toml
[bot]
token = '' ## Mandatory : Your bot token
status = '' ## Optional : The status of your bot (ONLINE / IDLE / DO_NOT_DISTURB / INVISIBLE / OFFLINE )
activity = '' ## Optional : Type of activity ( PLAYING / STREAMING / LISTENING / CUSTOM_STATUS / COMPETING ) + " " + text of activity
prefix = '' ## Optional : The prefix of raw text commands. Default value : '!'
intents = [] ## Mandatory : The string names of the intents that you bot enable

## Optional : your database information (all fields are mandatory if you use a database)
[database]
url = '' ## The url that points to the database to use
user = '' ## The name of the user that has permissions on the database
password = '' ## The given user's password

[log]
debug = true ## Whether the debug logs are enabled or not (true : logging whenever event handlers, commands, etc. are used)
```

Then, you will create a 'commands' folder in your bot directory, which will contain two 'raw' and 'slash' folders.
Those will be useful in the future.

### Javacord Setup

First, you need to determine the path of a directory that will contain all the configuration of your bot.
We will refer to this directory as your bot directory.
In your Java Main file, you will now use the following code :

```java
public static void main(String[] args) {
    String directoryPath = "...";
    Javacord.setup(directoryPath);
}
```

By giving your information, Javacord will create and configure your directory for you.
You can jump to the next step if no error is shown while running this code.

## Connect the bot

After the setup is done, you can start to code for real. In your Main file, you should add the following code :

```java
public static void main(String[] args) {
    String directoryPath = "...";
    Bot bot = new Bot(directoryPath);
}
```

By running this simple code, your bot should be online !

## Event Handling

To handle a specific type of Event, you need to create a class that implements the [EventHandler<?>](src/main/java/fr/dtn/javacord/event/EventHandler.java) interface, as shown below :

```java
public class ReadyHandler implements EventHandler<ReadyEvent> {
    @Override
    public void onEvent(Bot bot, ReadyEvent readyEvent) {
        // Your code logic here
    }
}
```

The type of event handled is specified as a generic type of the interface. To handle events correctly, please refer to the [JDA Documentation](https://jda.wiki/introduction/jda/).
Then, you need to register this event handler to the bot :

```java
public static void main(String[] args) {
    String directoryPath = "...";
    Bot bot = new Bot(directoryPath);

    bot.registerEventHandler(new ReadyHandler());
}
```

## Raw text commands

Raw text commands are basic Discord commands.
They can be called using a prefix (defined in your 'config.toml' file), and a label to call it.
Every message that starts with your prefix is considered as a raw command.
This system was used before the creation of the slash commands.
It is pretty useless now. However, here's how to create one :

### Create the command configuration

First, you need to create a configuration file in your 'commands/raw' folder of your bot directory.
Here's an example configuration file for a command whose purpose is to check whether the bot responds or not.

```toml
name = 'Ping'
description = 'A command to test the bot'
calls = ['ping']
permissions = []
executor = 'com.example.PingCommand'
```

The calls refer to all strings that can be used to use the command.
As an example, to use the command described, I would have to type "$ping" in a channel (if we consider that "$" was my raw command prefix).

Now we need to code what happens when a member uses our command.
To do that, we need to create a class that implements the [RawCommandExecutor](src/main/java/fr/dtn/javacord/commands/raw/RawCommandExecutor.java) interface :

```java
package com.example;

public class PingCommand implements RawCommandExecutor {
    @Override
    public void execute(Bot bot, Guild guild, TextChannel textChannel, Message message, User user, Member member, String[] args) {
        // Your code logic here
    }
}
```

The class package and name have to be matching with the ones given in the configuration file of the command.
Otherwise, an error will occur.

## Slash commands

First, you need to create a configuration file in your 'commands/slash' folder of your bot directory.
Here's an example configuration file for a command whose purpose is delete the given amount of latest message in a channel.

```toml
name = 'clear'
description = 'A command that clears messages from a given channel.'
permissions = ['MESSAGE_MANAGE']
executor = 'com.example.ClearCommand'

[parameters]
name = ['messages']
description = ['The number of messages to clear']
required = [true]
autoComplete = [false]
choice = [[]]
type = ['INTEGER']
```

Even if the autocomplete is turned off, you must let an empty list in the corresponding choice index, as shown in the example.

Here's the corresponding executor class :

```java
public class ClearCommand implements SlashCommandExecutor {
    @Override
    public void execute(Bot bot, SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();

        event.deferReply(true).queue(hook -> {
            int toDelete = Objects.requireNonNull(event.getOption("messages")).getAsInt();

            if (toDelete < 2 || toDelete > 100) {
                bot.sendTemporalMessage(hook, "You can delete from 2 to 100 messages.", 20, TimeUnit.SECONDS);
                return;
            }

            channel.getHistory().retrievePast(toDelete).queue(messages -> {
                if (messages.isEmpty()) {
                    bot.sendTemporalMessage(hook, "There is no message to delete...", 20, TimeUnit.SECONDS);
                } else if(messages.size() == 1) {
                    messages.get(0).delete().queue(success -> {
                        bot.sendTemporalMessage(hook, "1 message deleted !", 10, TimeUnit.SECONDS);
                    });
                } else {
                    channel.deleteMessages(messages).queue(success -> {
                        bot.sendTemporalMessage(hook, messages.size() + " messages deleted !", 10, TimeUnit.SECONDS);
                    });
                }
            });
        });
    }
}
```

The class package and name have to be matching with the ones given in the configuration file of the command.
Otherwise, an error will occur.

It's possible that the slash commands are not implemented as soon as you've created them.
This issue is Discord's fault.
Try kicking and re-inviting your bot to update its slash commands.
If it's not working, there's probably a problem with your command configuration : check the console to see if the command is effectively registered.

## Database connection

Javacord implements a basic system to use databases (with either MySQL, PostgresSQL or H2).
To use it, you have to specify your database information so that Javacord can establish the connection.
Then, with the instance of the bot you've created, you can use the "bot.getDatabase()" method to interact with the database.
