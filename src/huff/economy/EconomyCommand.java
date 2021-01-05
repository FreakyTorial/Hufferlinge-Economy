package huff.economy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableMap;

import huff.economy.storage.Bank;
import huff.economy.storage.Storage;
import huff.lib.helper.MessageHelper;
import huff.lib.helper.PermissionHelper;
import huff.lib.helper.StringHelper;
import huff.lib.helper.UserHelper;
import huff.lib.various.HuffCommand;

public class EconomyCommand extends HuffCommand
{
	private static final int LISTENTRIES_PER_PAGE = 2;
	
	public EconomyCommand(@NotNull EconomyInterface economy)
	{
		super(economy.getPlugin(), "economy");
		
		Validate.notNull((Object) economy, "The economy-interface cannot be null.");
		
		this.economy = economy;
		this.setDescription("Hufferlinge Economy Verwaltung");
		this.setUsage(StringHelper.build("\n \n§8☷ §7/economy\n",
						                 "§8☷ §7list <Seite>\n",
						                 "§8☷ §7balance [show|set|add|remove] (<Wert>) (<Spieler>)\n", 
						                 "§8☷ §7wallet [show|set|add|remove] (<Wert>) (<Spieler>)\n",
						                 "§8☷ §7bank [show|item|add|remove]"));
		this.setAliases("huffeconomy", "huffconomy", "money");
		this.setPermission(PermissionHelper.PERM_ROOT_HUFF + "economy");
		addTabCompletion();
		this.registerCommand();
	}

	private final EconomyInterface economy;
	
	// C O M M A N D

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		if (args.length > 0)
		{
			final String firstArgument = args[0];
			
			if (args.length >= 2)
			{
				if (firstArgument.equalsIgnoreCase("list"))
				{
					executeList(sender, args);
					return true;
				}
				else if (firstArgument.equalsIgnoreCase("balance"))
				{
					return executeValueAction(sender, args, true);
				}
				else if (firstArgument.equalsIgnoreCase("wallet"))
				{
					return executeValueAction(sender, args, false);
				}
				else if (firstArgument.equalsIgnoreCase("bank"))
				{
					return executeBankAction(sender, args);
				}
			}	
		}	
		return false;
	}
	
	// L I S T
	
	private void executeList(CommandSender sender, String[] args)
	{
		try 
		{
			final int page = Integer.parseInt(args[1]);
			final int maxPage = getListPageCount();
			
			if (page < 1 || page > maxPage)
			{
				sender.sendMessage(MessageHelper.PREFIX_HUFF + "Ungültige Seite. Es gibt nur" + MessageHelper.getHighlighted(Integer.toString(maxPage)) + "Seiten.");
				return;
			}			
			final List<String> economyOverview = economy.getStorage().getEconomyOverview((page - 1) * LISTENTRIES_PER_PAGE, page * LISTENTRIES_PER_PAGE);
			
			if (!economyOverview.isEmpty())
			{
				sender.sendMessage("§8☰ §7Übersicht über die Kontostände - Seite (" + page + "/" + maxPage + ")");
				sender.sendMessage("");
				
				for (String economyEntry : economyOverview)
				{
					sender.sendMessage(economyEntry);
				}	
			}
			else
			{
				sender.sendMessage("§8☰ §7Keine Spieler zur Übersicht vorhanden");
			}
		}
		catch (NumberFormatException exception)
		{
			sender.sendMessage(MessageHelper.PREFIX_HUFF + MessageHelper.getQuoted(args[1], false, true) + "ist keine gültige Seite.");
		}
	}
	
	private int getListPageCount()
	{
		return (int) Math.ceil((double) economy.getStorage().getUserCount() / LISTENTRIES_PER_PAGE);
	}
	
	// V A L U E
	
	private boolean executeValueAction(CommandSender sender, String[] args, boolean isBalance)
	{
		switch (args[1].toLowerCase())
		{
		case "show":
			executeValueShow(sender, args, isBalance);	
			return true;
		case "set":
			executeValueSet(sender, args, isBalance);
			return true;
		case "add":
			executeValueUpdate(sender, args, isBalance, false);
			return true;
		case "remove":
			executeValueUpdate(sender, args, isBalance, true);
			return true;
		default:
			return false;
		}
	}
	
	private void executeValueShow(CommandSender sender, String[] args, boolean isBalance)
	{
		if (args.length >= 3)
		{			
			final String targetPlayerName = args[2]; 
			final UUID targetPlayer = UserHelper.getUniqueId(targetPlayerName);
			
			if (targetPlayer == null)
			{
				sender.sendMessage(MessageHelper.getPlayerNotFound(targetPlayerName));
				return;
			}
			sender.sendMessage(processGetValue(isBalance, targetPlayer, targetPlayerName));
		}
		else if (sender instanceof Player)
		{
			sender.sendMessage(processGetValue(isBalance, ((Player) sender).getUniqueId(), null));
		}
		else
		{
			sender.sendMessage(getInvalidSenderMessage());
		}
	}
	
	private @NotNull String processGetValue(boolean isBalance, @NotNull UUID targetUUID, @Nullable String targetName)
	{
		final double value = isBalance ? economy.getStorage().getBalance(targetUUID) : economy.getStorage().getWallet(targetUUID);
		final int feedbackCode = value >= 0 ? Storage.CODE_SUCCESS : Storage.CODE_NOUSER;
		
		return processFeedbackCode(feedbackCode, value, isBalance, false, false, targetName, null);
	}
	
	private void executeValueSet(CommandSender sender, String[] args, boolean isBalance)
	{
		final double value = parseDoubleInput(sender, args[2]);			
		
		if (value == -1)
		{
			return;
		}	
		
		if (args.length >= 4)
		{			
			final String targetPlayerName = args[3]; 
			final UUID targetPlayer = UserHelper.getUniqueId(targetPlayerName);
			
			if (targetPlayer == null)
			{
				sender.sendMessage(MessageHelper.getPlayerNotFound(targetPlayerName));
				return;
			}
			sender.sendMessage(procesSetValue(isBalance, value, targetPlayer, targetPlayerName));
		}
		else if (sender instanceof Player)
		{
			sender.sendMessage(procesSetValue(isBalance, value, ((Player) sender).getUniqueId(), null));
		}
		else
		{
			sender.sendMessage(getInvalidSenderMessage());
		}
	}
	
	private @NotNull String procesSetValue(boolean isBalance, double value, @NotNull UUID targetUUID, @Nullable String targetName)
	{
		final int feedbackCode = isBalance ? economy.getStorage().setBalance(targetUUID, value) : economy.getStorage().setWallet(targetUUID, value);
		
		return processFeedbackCode(feedbackCode, value, isBalance, false, true, targetName, targetUUID); 
	}
	
	private void executeValueUpdate(CommandSender sender, String[] args, boolean isBalance, boolean isRemove) 
	{
		final double value = parseDoubleInput(sender, args[2]);			
		
		if (value == -1)
		{
			return;
		}	
		
		if (args.length >= 4)
		{			
			final String targetPlayerName = args[3]; 
			final UUID targetPlayer = UserHelper.getUniqueId(targetPlayerName);
			
			if (targetPlayer == null)
			{
				sender.sendMessage(MessageHelper.getPlayerNotFound(targetPlayerName));
				return;
			}
			sender.sendMessage(processUpdateValue(isBalance, isRemove, value, targetPlayer, targetPlayerName));
		}
		else if (sender instanceof Player)
		{
			sender.sendMessage(processUpdateValue(isBalance, isRemove, value, ((Player) sender).getUniqueId(), null));
		}
		else
		{
			sender.sendMessage(getInvalidSenderMessage());
		}
	}
	
	private @NotNull String processUpdateValue(boolean isBalance, boolean isRemove, double value, @NotNull UUID targetUUID, @Nullable String targetName)
	{
		final int feedbackCode = (economy.getStorage().updateValue(targetUUID, value, isRemove, isBalance));
		
		return processFeedbackCode(feedbackCode, value, isBalance, isRemove, false, targetName, targetUUID);
	}
	
	private double parseDoubleInput(CommandSender sender, String input)
	{
		try 
		{
			final double parsedValue =  Double.parseDouble(input);
			
			if (parsedValue < 0)
			{
				sender.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, "Der eingegebene Wert darf nicht negativ sein."));
				return -1;
			}		
			return parsedValue;
		}
		catch (NumberFormatException execption)
		{
			sender.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, "Der eingegebene Wert ist ungültig."));
		}
		return -1;
	}
	
	private String processFeedbackCode(int code, double value, boolean isBalance, boolean withRemove, boolean override, String playerName, UUID playerUUID)
	{
		final StringBuilder messageBuilder = new StringBuilder();
		final boolean selfPerform = StringUtils.isEmpty(playerName);
		final boolean updatedPerform = playerUUID != null;
		
		messageBuilder.append(MessageHelper.PREFIX_HUFF);
		
		if (selfPerform)
		{
			messageBuilder.append("Du ");
		}
		else
		{
			messageBuilder.append("Der Spieler");
			messageBuilder.append(MessageHelper.getHighlighted(playerName));
		}
		
		switch (code)
		{
		case Storage.CODE_NOUSER:
			messageBuilder.append(selfPerform ? "bist " : "ist ");
			messageBuilder.append("nicht in der Economy-Datenbank vorhanden.");
			break;
		case Storage.CODE_NOTENOUGHVALUE:
			messageBuilder.append(selfPerform ? "hast " : "hat ");
			messageBuilder.append("dazu nicht genug ");
			messageBuilder.append(isBalance ? "auf der Bank." : "im Geldbeutel.");
			break;
		case Storage.CODE_SUCCESS:
			messageBuilder.append(selfPerform ? "hast " : "hat ");
			messageBuilder.append(updatedPerform || override ? "nun " : "");
			messageBuilder.append(MessageHelper.getHighlighted(economy.getConfig().getValueFormatted(value), false, true));
			if (updatedPerform) messageBuilder.append(withRemove ? "weniger " : "mehr ");
			messageBuilder.append(isBalance ? "auf der Bank." : "im Geldbeutel.");
			if (updatedPerform)
			{
				messageBuilder.append("\n");
				messageBuilder.append(MessageHelper.PREFIX_HUFF);
				messageBuilder.append("Der neue Stand beträgt");
				messageBuilder.append(MessageHelper.getHighlighted(economy.getConfig().getValueFormatted(isBalance ? economy.getStorage().getBalance(playerUUID) : 
						                                                                                             economy.getStorage().getWallet(playerUUID)), true, false));
				messageBuilder.append(".");
			}
			break;
		default:
			return MessageHelper.PREFIX_HUFF + "Ungültiger Datenbank-Rückgabecode" + MessageHelper.getQuoted(Integer.toString(code), true, false) + ".";
		}		
		return messageBuilder.toString();
	}

	private @NotNull String getInvalidSenderMessage()
	{
		return StringHelper.build(MessageHelper.PREFIX_HUFF, "Du kannst diesen Befehl nicht auf dich selbst aufrufen.");
	}
	
	// B A N K
	
	private boolean executeBankAction(CommandSender sender, String[] args)
	{
		final String action = args[1].toLowerCase();
		
		if (!action.equals("show") && !(sender instanceof Player))
		{
			sender.sendMessage(MessageHelper.NORUNINCONSOLE);
			return true;
		}
		
		switch (action)
		{
		case "show":
			executeBankShow(sender);	
			return true;
		case "add":
			executeBankAdd((Player) sender);
			return true;
		case "remove":
			executeBankRemove((Player) sender);
			return true;
		case "item":
			executeBankItem((Player) sender);
			return true;
		default:
			return false;
		}
	}
	
	private void executeBankShow(CommandSender sender)
	{
		final List<Location> bankLocations = economy.getBank().getBankLocations();
		
		if (!bankLocations.isEmpty())
		{
			sender.sendMessage("§8☰ §7Übersicht aller Bänker");
			sender.sendMessage("");
			
			int position = 1;
			
			for (Location bankLocation : bankLocations)
			{
				final World bankLocationWorld = bankLocation.getWorld();
				
				if (bankLocationWorld == null)
				{
					continue;
				}
				final boolean sameWorld = sender instanceof Player && ((Player) sender).getWorld().equals(bankLocationWorld);
				
				sender.sendMessage(String.format("§8☰  §a%d §8- §7Welt: §9%s\n" + 
			                                     "§8☷ §7Koordinaten: §9%.0f %.0f %.0f §8× §7Distanz: §9%.2f", 
			                                     position, bankLocationWorld.getName(), bankLocation.getX(), 
			                                     bankLocation.getY(), bankLocation.getZ(),
			                                     sameWorld ? ((Player) sender).getLocation().distance(bankLocation) : -1));
				position++;
			}	
		}
		else
		{
			sender.sendMessage("§8☰ §7Keine Bänker zur Übersicht vorhanden");
		}	
	}
	
	private void executeBankAdd(Player player)
	{
		final Location playerLocation = player.getLocation();
		final Location bankLocation = new Location(playerLocation.getWorld(), playerLocation.getBlockX() + 0.5, playerLocation.getBlockY(), playerLocation.getBlockZ() + 0.5,
				                                   playerLocation.getYaw(), 0);		
		
		if (economy.getBank().addBank(bankLocation, player.getUniqueId()) == Bank.CODE_SUCCESS)
		{
			economy.trySpawnBankEntity(bankLocation);
			player.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, economy.getConfig().getBankName(), " platziert.\n"));
		}
		else
		{
			player.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, "Du bist zu nah an einem anderen ", economy.getConfig().getBankName(), "."));
		}
	}
	
	private void executeBankRemove(Player player)
	{
		if (economy.getBank().removeBank(player.getLocation()) == Bank.CODE_SUCCESS)
		{
			economy.tryRemoveBankEntity(player.getLocation());
			player.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, economy.getConfig().getBankName(), " entfernt."));
		}
		else
		{
			player.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, "Du bist nicht in der Nähe von einem ", economy.getConfig().getBankName(), "."));
		}	
	}
	
	private void executeBankItem(Player player)
	{
		player.getInventory().addItem(economy.getConfig().getBankSpawnItem());
		player.sendMessage(StringHelper.build(MessageHelper.PREFIX_HUFF, "Du hast das ", economy.getConfig().getBankName(), "-Spawn-Item bekommen."));		
	}

	// T A B C O M P L E T E
	
	private void addTabCompletion()
	{
		final Map<Integer, List<String>> showBeforeText = ImmutableMap.of(
				0, Stream.of("balance", "wallet").collect(Collectors.toList()), 
				1, Stream.of("show").collect(Collectors.toList()));
		final Map<Integer, List<String>> valueBeforeText = ImmutableMap.of(
				0, Stream.of("balance", "wallet").collect(Collectors.toList()), 
				1, Stream.of("set", "add", "remove").collect(Collectors.toList()));
		final String[] listPages = Stream.iterate(1, x -> x + 1)
				.limit(getListPageCount())
				.map(x -> Integer.toString(x))
				.toArray(String[]::new);
		final String[] players = Stream.of(Bukkit.getOfflinePlayers())
				.map(OfflinePlayer::getName)
				.toArray(String[]::new); 
		
		this.addTabCompletion(0, "list", "balance", "wallet", "bank");
		this.addTabCompletion(1, null, Stream.of("list").toArray(String[]::new), listPages);
		this.addTabCompletion(1, null, Stream.of("balance", "wallet").toArray(String[]::new), "show", "set", "add", "remove");
		this.addTabCompletion(1, null, Stream.of("bank").toArray(String[]::new), "show", "item", "add", "remove");
		this.addTabCompletion(2, null, showBeforeText, players);
		this.addTabCompletion(2, null, valueBeforeText, "<Wert>");
		this.addTabCompletion(3, null, valueBeforeText, players);
	}
}
