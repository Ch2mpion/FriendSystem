package org.ch2mpion.friendsystem.commands;

import dev.velix.imperat.BukkitSource;
import dev.velix.imperat.command.CommandUsage;
import dev.velix.imperat.context.ExecutionContext;
import dev.velix.imperat.exception.ImperatException;
import dev.velix.imperat.exception.NoHelpException;
import dev.velix.imperat.help.HelpProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class FriendHelpProvider implements HelpProvider<BukkitSource> {


    @Override
    public void provide(ExecutionContext<BukkitSource> context, BukkitSource source) throws ImperatException {

        var src = context.source();
        var cmd = context.command();

        if(cmd.usages().isEmpty()) {
            throw new NoHelpException();
        }

        src.reply("&b&lFRIEND COMMANDS");
        src.reply("&7--------------------------------------------");

        for(var usage : cmd.usages()) {
            //&7- Reject a friend request.
            //&b/friend reject &e<player>

            Pattern pattern = Pattern.compile("<player>");
            String text = CommandUsage.format(cmd,usage);
            Matcher matcher = pattern.matcher(text);

            StringBuffer sb = new StringBuffer();


            while(matcher.find()) {
                matcher.appendReplacement(sb, "&e" + matcher.group() + "&b");
                text = sb.toString();
            }

            src.reply("&a[+] &b/" + text + " &7- " + usage.description());


        }

        src.reply("&7--------------------------------------------");

    }
}
