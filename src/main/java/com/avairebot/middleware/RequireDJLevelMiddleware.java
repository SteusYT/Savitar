/*
 * Copyright (c) 2018.
 *
 * This file is part of AvaIre.
 *
 * AvaIre is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AvaIre is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AvaIre.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.avairebot.middleware;

import com.avairebot.AvaIre;
import com.avairebot.audio.AudioHandler;
import com.avairebot.audio.DJGuildLevel;
import com.avairebot.commands.CommandContainer;
import com.avairebot.commands.CommandHandler;
import com.avairebot.commands.CommandMessage;
import com.avairebot.commands.administration.IAmCommand;
import com.avairebot.contracts.middleware.DJCheckMessage;
import com.avairebot.contracts.middleware.Middleware;
import com.avairebot.database.transformers.GuildTransformer;
import com.avairebot.factories.MessageFactory;
import com.avairebot.language.I18n;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class RequireDJLevelMiddleware extends Middleware {

    public RequireDJLevelMiddleware(AvaIre avaire) {
        super(avaire);
    }

    @Nullable
    @Override
    public String buildHelpDescription(@Nonnull CommandMessage context, @Nonnull String[] args) {
        if (!context.isGuildMessage() || args.length == 0) {
            return "**You need the `DJ` role to use this command**";
        }

        GuildTransformer guildTransformer = context.getGuildTransformer();
        if (guildTransformer == null) {
            return "**You need the `DJ` role to use this command**";
        }

        // Incase the server has the DJ level set to "ALL", we'll just not send any DJ level
        // message, since everyone can use all music commands anyways.
        if (guildTransformer.getDJLevel().equals(DJGuildLevel.ALL)) {
            return null;
        }

        Role role = getDjRoleFrom(guildTransformer, context.getMessage());
        if (role == null) {
            return "**You need the `DJ` role to use this command**";
        }

        return I18n.format("**You need the {0} role to use this command**",
            role.getAsMention()
        );
    }

    @Override
    public boolean handle(@Nonnull Message message, @Nonnull MiddlewareStack stack, String... args) {
        if (!message.getChannelType().isGuild()) {
            return stack.next();
        }

        if (args.length > 0) {
            DJGuildLevel level = DJGuildLevel.fromName(args[0]);

            if (level != null && AudioHandler.getDefaultAudioHandler().canRunDJAction(avaire, message, level)) {
                return stack.next();
            }

            return sendErrorMessage(message, stack);
        }

        if (AudioHandler.getDefaultAudioHandler().canRunDJAction(avaire, message, DJGuildLevel.NORMAL)) {
            return stack.next();
        }

        return sendErrorMessage(message, stack);
    }

    @SuppressWarnings("ConstantConditions")
    private boolean sendErrorMessage(Message message, MiddlewareStack stack) {
        return runMessageCheck(message, () -> {
            String djcheckMessage = "The :role Discord role is required to run this command!";

            DJCheckMessage annotation = stack.getCommand().getClass().getAnnotation(DJCheckMessage.class);
            if (annotation != null && annotation.message().trim().length() > 0) {
                if (annotation.overwrite()) {
                    djcheckMessage = annotation.message();
                } else {
                    djcheckMessage += annotation.message();
                }
            }

            GuildTransformer guildTransformer = stack.getDatabaseEventHolder().getGuild();
            Role djRole = getDjRoleFrom(guildTransformer, message);

            if (djRole != null && guildTransformer != null && guildTransformer.getSelfAssignableRoles().containsValue(djRole.getName().toLowerCase())) {
                djcheckMessage += "\nYou can use the `:iam :roleName` command to get the role!";
            }

            CommandContainer command = CommandHandler.getCommand(IAmCommand.class);
            MessageFactory.makeError(message, djcheckMessage)
                .set("iam", command.getCommand().generateCommandTrigger(message))
                .set("prefix", stack.getCommand().generateCommandPrefix(message))
                .set("role", djRole == null ? "`DJ`" : djRole.getAsMention())
                .set("roleName", djRole == null ? "DJ" : djRole.getName())
                .queue(newMessage -> newMessage.delete().queueAfter(45, TimeUnit.SECONDS));

            return false;
        });
    }

    private Role getDjRoleFrom(GuildTransformer guildTransformer, Message message) {
        if (guildTransformer == null) {
            return null;
        }

        if (guildTransformer.getDjRole() != null) {
            Role role = message.getGuild().getRoleById(guildTransformer.getDjRole());
            if (role != null) {
                return role;
            }
        }

        return null;
    }
}
