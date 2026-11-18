package com.mohamed.presight.command;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.keybind.Keybinds;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /presight} — list the features, their keys and their state.
 *
 * {@code /presight toggle <id>} exists so a feature can be driven from a macro or a
 * command block binding without touching the keyboard.
 */
public class PreSightCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "presight";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("ps");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/presight [toggle <feature>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "toggle", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            List<String> ids = new ArrayList<>();
            for (Feature f : FeatureRegistry.all()) {
                ids.add(f.id());
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 2 && args[0].equalsIgnoreCase("toggle")) {
            String id = args[1].toLowerCase();
            if (FeatureRegistry.byId(id) == null) {
                throw new CommandException("Unknown feature: " + id);
            }
            boolean on = FeatureRegistry.toggle(id);
            reply(sender, EnumChatFormatting.GRAY + id + ": "
                    + (on ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF"));
            return;
        }

        reply(sender, EnumChatFormatting.AQUA + "PreSight "
                + com.mohamed.presight.PreSight.VERSION);
        for (Feature f : FeatureRegistry.all()) {
            boolean on = FeatureRegistry.isEnabled(f.id());
            reply(sender, EnumChatFormatting.GRAY + "  " + Keybinds.keyName(f)
                    + "  " + EnumChatFormatting.WHITE + f.displayName()
                    + EnumChatFormatting.DARK_GRAY + " (" + f.id() + ")  "
                    + (on ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF"));
        }
        reply(sender, EnumChatFormatting.DARK_GRAY + "/presight toggle <id>");
    }

    private static void reply(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(msg));
    }
}
