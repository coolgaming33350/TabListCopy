package hu.montlikadani.tablist.utils.variables;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import hu.montlikadani.tablist.config.constantsLoader.ConfigValues;
import hu.montlikadani.tablist.config.constantsLoader.TabConfigValues;
import hu.montlikadani.tablist.user.TabListUser;
import hu.montlikadani.tablist.TabList;
import hu.montlikadani.tablist.api.TabListAPI;
import hu.montlikadani.tablist.utils.PluginUtils;
import hu.montlikadani.tablist.utils.ServerVersion;
import hu.montlikadani.tablist.utils.operators.ExpressionNode;
import hu.montlikadani.tablist.utils.operators.OperatorNodes;
import me.clip.placeholderapi.PlaceholderAPI;

public final class Variables {

	private final TabList plugin;

	private final List<ExpressionNode> nodes = new ArrayList<>();
	private final java.util.Set<Variable> variables = new java.util.HashSet<>(8);

	public Variables(TabList plugin) {
		this.plugin = plugin;
	}

	public void load() {
		nodes.clear();
		variables.clear();

		if (ConfigValues.isPingFormatEnabled()) {
			for (String f : ConfigValues.getPingColorFormats()) {
				if (f.isEmpty()) {
					continue;
				}

				ExpressionNode node = new OperatorNodes(OperatorNodes.NodeType.PING);
				node.setParseExpression(f);

				if (node.getCondition() != null) {
					nodes.add(node);
				}
			}
		}

		if (ConfigValues.isTpsFormatEnabled()) {
			for (String f : ConfigValues.getTpsColorFormats()) {
				if (f.isEmpty()) {
					continue;
				}

				ExpressionNode node = new OperatorNodes(OperatorNodes.NodeType.TPS);
				node.setParseExpression(f);

				if (node.getCondition() != null) {
					nodes.add(node);
				}
			}
		}

		int size = nodes.size();

		// Sort
		// ping in descending order
		// tps in ascending order
		for (int i = 0; i < size; i++) {
			for (int j = size - 1; j > i; j--) {
				ExpressionNode node = nodes.get(i), node2 = nodes.get(j);

				boolean firstPing = node.getType() == OperatorNodes.NodeType.PING;

				if ((firstPing && node2.getType() == OperatorNodes.NodeType.PING
						&& node.getCondition().getSecondCondition() < node2.getCondition().getSecondCondition())
						|| (firstPing && node2.getType() == OperatorNodes.NodeType.TPS && node.getCondition()
								.getSecondCondition() > node2.getCondition().getSecondCondition())) {
					nodes.set(i, node2);
					nodes.set(j, node);
				}
			}
		}

		if (ConfigValues.getDateFormat() != null) {
			variables.add(new Variable("date", 3).setVariable((v, str) -> str = str.replace(v.fullName,
					v.setAndGetRemainingValue(getTimeAsString(ConfigValues.getDateFormat())))));
		}

		variables.add(new Variable("online-players", 2).setVariable((v, str) -> {
			int players = PluginUtils.countVanishedPlayers();

			if (ConfigValues.isCountFakePlayersToOnlinePlayers()) {
				players += plugin.getFakePlayerHandler().getFakePlayers().size();
			}

			str = str.replace(v.fullName, v.setAndGetRemainingValue(Integer.toString(players)));
		}));

		variables.add(new Variable("max-players", 20).setVariable((v, str) -> str = str.replace(v.fullName,
				v.setAndGetRemainingValue(Integer.toString(plugin.getServer().getMaxPlayers())))));

		variables.add(new Variable("vanished-players", 2).setVariable((v, str) -> {
			int vanishedPlayers = PluginUtils.getVanishedPlayers();

			str = str.replace(v.fullName,
					v.setAndGetRemainingValue(vanishedPlayers == 0 ? "0" : Integer.toString(vanishedPlayers)));
		}));

		variables.add(new Variable("servertype", -1).setVariable(
				(v, str) -> str = str.replace(v.fullName, v.setAndGetRemainingValue(plugin.getServer().getName()))));

		variables.add(new Variable("mc-version", -1).setVariable((v, str) -> str = str.replace(v.fullName,
				v.setAndGetRemainingValue(plugin.getServer().getBukkitVersion()))));

		variables.add(new Variable("motd", 10).setVariable((v,
				str) -> str = str.replace(v.fullName, v.setAndGetRemainingValue(plugin.getComplement().getMotd()))));

		variables.add(new Variable("fake-players", 3).setVariable((v, str) -> {
			int pls = plugin.getFakePlayerHandler().getFakePlayers().size();

			str = str.replace(v.fullName, v.setAndGetRemainingValue(pls == 0 ? "0" : Integer.toString(pls)));
		}));

		variables.add(new Variable("staff-online", 3).setVariable((v, str) -> {
			int staffs = 0;

			for (TabListUser user : plugin.getUsers()) {
				Player player = user.getPlayer();

				if (player == null || !PluginUtils.hasPermission(player, "tablist.onlinestaff")
						|| (!ConfigValues.isCountVanishedStaff() && PluginUtils.isVanished(player))) {
					continue;
				}

				staffs++;
			}

			str = str.replace(v.fullName, v.setAndGetRemainingValue(staffs == 0 ? "0" : Integer.toString(staffs)));
		}));
	}

	private final long MB = 1024 * 1024;

	public String replaceVariables(Player pl, String str) {
		if (str.isEmpty()) {
			return str;
		}

		Runtime runtime = Runtime.getRuntime();

		if (!ConfigValues.getMemoryBarChar().isEmpty() && str.indexOf("%memory_bar%") >= 0) {
			StringBuilder builder = new StringBuilder();

			int barSize = ConfigValues.getMemoryBarSize(), totalMemory = (int) (runtime.totalMemory() / MB),
					usedMemory = totalMemory - (int) (runtime.freeMemory() / MB),
					maxMemory = (int) (runtime.maxMemory() / MB);

			float usedMem = (float) usedMemory / maxMemory;
			float totalMem = (float) totalMemory / maxMemory;

			String barChar = ConfigValues.getMemoryBarChar();

			builder.append(
					usedMem < 0.8 ? ConfigValues.getMemoryBarUsedColor() : ConfigValues.getMemoryBarAllocationColor());

			int totalBarSize = (int) (barSize * usedMem);
			for (int i = 0; i < totalBarSize; i++) {
				builder.append(barChar);
			}

			builder.append(ConfigValues.getMemoryBarFreeColor());

			totalBarSize = (int) (barSize * (totalMem - usedMem));
			for (int i = 0; i < totalBarSize; i++) {
				builder.append(barChar);
			}

			builder.append(ConfigValues.getMemoryBarReleasedColor());

			totalBarSize = (int) (barSize * (1 - totalMem));
			for (int i = 0; i < totalBarSize; i++) {
				builder.append(barChar);
			}

			str = str.replace("%memory_bar%", builder.toString());
		}

		// TODO Remove or make more customisable variables
		for (java.util.Map.Entry<String, String> map : TabConfigValues.CUSTOM_VARIABLES.entrySet()) {
			str = str.replace(map.getKey(), map.getValue());
		}

		if (pl != null) {
			str = setPlayerPlaceholders(pl, str);
		}

		for (Variable variable : variables) {
			if (variable.isReplacedBefore() && variable.getRemainingValue() != null) {
				str = str.replace(variable.fullName, variable.getRemainingValue());
				continue;
			}

			if (variable.canReplace(str)) {
				variable.getReplacer().accept(variable, str);
			}

			if (variable.getRemainingValue() != null) {
				str = str.replace(variable.fullName, variable.getRemainingValue());
			}
		}

		if (ConfigValues.getTimeFormat() != null && str.indexOf("%server-time%") >= 0) {
			str = str.replace("%server-time%", getTimeAsString(ConfigValues.getTimeFormat()));
		}

		if (str.indexOf("%server-ram-free%") >= 0) {
			str = str.replace("%server-ram-free%", Long.toString(runtime.freeMemory() / MB));
		}

		if (str.indexOf("%server-ram-max%") >= 0) {
			str = str.replace("%server-ram-max%", Long.toString(runtime.maxMemory() / MB));
		}

		if (str.indexOf("%server-ram-used%") >= 0) {
			str = str.replace("%server-ram-used%", Long.toString((runtime.totalMemory() - runtime.freeMemory()) / MB));
		}

		if (str.indexOf("%tps-overflow%") >= 0) {
			str = str.replace("%tps-overflow%", tpsDot(TabListAPI.getTPS()));
		}

		if (str.indexOf("%tps%") >= 0) {
			double tps = TabListAPI.getTPS();
			str = str.replace("%tps%", (tps > 20.0 ? "*" : "") + tpsDot(tps > 20.0 ? 20D : tps));
		}

		// Don't use here colors because of some issues with hex
		return str;
	}

	@SuppressWarnings("deprecation")
	String setPlayerPlaceholders(Player p, String s) {
		// TODO we need optimization for these variables so that there is only a
		// one-time check for placeholders at load time.

		if (plugin.hasPapi()) {
			if (s.indexOf("server_total_chunks%") >= 0 || s.indexOf("server_total_living_entities%") >= 0
					|| s.indexOf("server_total_entities%") >= 0 || s.indexOf("%sync:") >= 0) {
				s = s.replace("sync:", "");

				final String str = s;

				s = hu.montlikadani.tablist.utils.task.Tasks.submitSync(() -> PlaceholderAPI.setPlaceholders(p, str));
			} else {
				s = PlaceholderAPI.setPlaceholders(p, s);
			}
		}

		s = s.replace("%player%", p.getName());
		s = s.replace("%player-uuid%", p.getUniqueId().toString());
		s = s.replace("%world%", p.getWorld().getName());
		s = s.replace("%player-gamemode%", p.getGameMode().name());

		if (!plugin.isPaper() || s.indexOf("%player-displayname%") >= 0) {
			s = s.replace("%player-displayname%", plugin.getComplement().getDisplayName(p));
		}

		if (s.indexOf("%player-health%") >= 0) {
			s = s.replace("%player-health%", Double.toString(p.getHealth()));
		}

		if (s.indexOf("%player-max-health%") >= 0) {
			if (ServerVersion.isCurrentLower(ServerVersion.v1_9_R1)) {
				s = s.replace("%player-max-health%", Double.toString(p.getMaxHealth()));
			} else {
				org.bukkit.attribute.AttributeInstance attr = p
						.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);

				if (attr != null) {
					s = s.replace("%player-max-health%", Double.toString(attr.getDefaultValue()));
				}
			}
		}

		if (s.indexOf("%ping%") >= 0) {
			s = s.replace("%ping%", formatPing(TabListAPI.getPing(p)));
		}

		if (s.indexOf("%exp-to-level%") >= 0) {
			s = s.replace("%exp-to-level%", Integer.toString(p.getExpToLevel()));
		}

		if (s.indexOf("%level%") >= 0) {
			s = s.replace("%level%", Integer.toString(p.getLevel()));
		}

		if (s.indexOf("%xp%") >= 0) {
			s = s.replace("%xp%", Float.toString(p.getExp()));
		}

		if (s.indexOf("%light-level%") >= 0) {
			s = s.replace("%light-level%", Integer.toString((int) p.getLocation().getBlock().getLightLevel()));
		}

		if (s.indexOf("%ip-address%") >= 0) {
			java.net.InetSocketAddress address = p.getAddress();

			if (address != null && address.getAddress() != null) {
				s = s.replace("%ip-address%", address.getAddress().toString().replace("/", ""));
			}
		}

		return s;
	}

	private String tpsDot(double d) {
		if (!ConfigValues.isTpsFormatEnabled() || nodes.isEmpty()) {
			return "" + d;
		}

		String ds = parseExpression(d, OperatorNodes.NodeType.TPS);
		int index = ds.indexOf('.');

		if (index >= 0) {
			int tpsSize = ConfigValues.getTpsSize();
			int size = (tpsSize == 1 ? 3 : index) + (tpsSize < 1 ? 2 : tpsSize);
			int length = ds.length();

			ds = ds.substring(0, size > length ? length : size);
		}

		return ds;
	}

	private String formatPing(int ping) {
		if (!ConfigValues.isPingFormatEnabled() || nodes.isEmpty()) {
			return "" + ping;
		}

		return parseExpression(ping, OperatorNodes.NodeType.PING);
	}

	private final Pattern colorVariablePattern = Pattern.compile("%tps%|%tps-overflow%|%ping%");

	private String parseExpression(double value, int type) {
		String color = "";

		for (ExpressionNode node : nodes) {
			if (node.getType() == type && node.parse(value)) {
				color = node.getCondition().getColor();
			}
		}

		StringBuilder builder = new StringBuilder();

		if (!color.isEmpty()) {
			builder.append(colorVariablePattern.matcher(color).replaceAll("").replace('&', '\u00a7'));
		}

		return (type == OperatorNodes.NodeType.PING ? builder.append((int) value) : builder.append(value)).toString();
	}

	private String getTimeAsString(DateTimeFormatter formatterPattern) {
		TimeZone zone = ConfigValues.isUseSystemZone() ? TimeZone.getTimeZone(java.time.ZoneId.systemDefault())
				: TimeZone.getTimeZone(ConfigValues.getTimeZone());
		LocalDateTime now = zone == null ? LocalDateTime.now() : LocalDateTime.now(zone.toZoneId());

		return now.format(formatterPattern);
	}
}